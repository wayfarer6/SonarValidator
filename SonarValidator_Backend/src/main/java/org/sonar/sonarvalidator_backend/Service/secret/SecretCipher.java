package org.sonar.sonarvalidator_backend.Service.secret;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class SecretCipher {

    private static final Logger log = LoggerFactory.getLogger(SecretCipher.class);

    private static final int IV_LENGTH = 12;

    private static final int TAG_LENGTH_BITS = 128;

    private static final int KEY_LENGTH_BYTES = 32;

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private final String configuredKey;

    private SecretKey key;

    private boolean usingEphemeralKey;

    private final SecureRandom random = new SecureRandom();

    public SecretCipher(@Value("${sonar.secret.key:}") String configuredKey) {
        this.configuredKey = configuredKey;
    }

    @PostConstruct
    void init() {
        if (configuredKey != null && !configuredKey.isBlank()) {
            try {
                final byte[] raw = Base64.getDecoder().decode(configuredKey.trim());
                if (raw.length != KEY_LENGTH_BYTES) {
                    throw new IllegalArgumentException(
                            "key must be " + KEY_LENGTH_BYTES + " bytes, got " + raw.length);
                }
                this.key = new SecretKeySpec(raw, "AES");
                this.usingEphemeralKey = false;
                log.info("secret cipher initialized with configured key");
                return;
            } catch (RuntimeException ex) {
                log.error("sonar.secret.key is invalid ({}); falling back to ephemeral key. "
                        + "Generate one with: openssl rand -base64 32", ex.getMessage());
            }
        } else {
            log.warn("sonar.secret.key is not set; using an ephemeral key. "
                    + "Encrypted values will NOT survive a restart. "
                    + "Set SONAR_SECRET_KEY for production.");
        }

        final byte[] generated = new byte[KEY_LENGTH_BYTES];
        random.nextBytes(generated);
        this.key = new SecretKeySpec(generated, "AES");
        this.usingEphemeralKey = true;
    }

    public boolean isUsingEphemeralKey() {
        return usingEphemeralKey;
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }
        try {
            final byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);

            final Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            final byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            final byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException ex) {
            // 암호화 실패 = 저장 실패로 이어져야 합니다. (평문 폴백 금지)
            log.error("failed to encrypt secret: {}", ex.getMessage());
            throw new IllegalStateException("secret encryption failed", ex);
        }
    }

    public String decrypt(String encrypted) {
        if (encrypted == null || encrypted.isEmpty()) {
            return encrypted;
        }
        try {
            final byte[] combined = Base64.getDecoder().decode(encrypted);
            if (combined.length <= IV_LENGTH) {
                throw new IllegalArgumentException("ciphertext too short");
            }
            final byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);

            final Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            final byte[] plaintext = cipher.doFinal(combined, IV_LENGTH, combined.length - IV_LENGTH);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "secret decryption failed (key changed, or value corrupted): " + ex.getMessage(), ex);
        }
    }

    public boolean isPresent(String encrypted) {
        return encrypted != null && !encrypted.isBlank();
    }
}
