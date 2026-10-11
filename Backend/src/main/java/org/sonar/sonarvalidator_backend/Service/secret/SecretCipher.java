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

/**
 * 저장이 필요한 비밀값(예: OPNsense API Secret)을 암호화/복호화합니다.
 *
 * <h2>알고리즘 선택</h2>
 * <p><b>AES-GCM</b> 을 씁니다. 같은 키로 같은 평문을 암호화해도 매번 다른
 * 암호문이 나오고(IV 가 랜덤), <b>변조 감지</b>가 내장되어 있습니다.
 * AES-CBC 는 무결성 검증이 없어 암호문을 조작당해도 알아채지 못합니다.
 *
 * <h2>저장 형식</h2>
 * <pre>
 *   base64( IV(12바이트) || ciphertext || GCM tag(16바이트) )
 * </pre>
 * <p>IV 를 암호문 앞에 붙여 저장합니다. IV 는 비밀이 아니며(그래서 함께
 * 저장해도 안전) 매 암호화마다 새로 만들어야 합니다. IV 를 재사용하면
 * GCM 의 보안이 무너집니다.
 *
 * <h2>키 관리</h2>
 * <p>키는 {@code sonar.secret.key} 프로퍼티(또는 {@code SONAR_SECRET_KEY}
 * 환경변수)에서 받습니다. <b>32바이트(256비트)</b> 를 Base64 로 인코딩한
 * 문자열이어야 합니다.
 *
 * <pre>
 *   # 새 키 만들기 (한 번만)
 *   openssl rand -base64 32
 *   # 출력값을 SONAR_SECRET_KEY 로 주입
 * </pre>
 *
 * <h2>⚠️ 키가 없을 때의 동작</h2>
 * <p>개발 편의를 위해 임시 키를 만들어 동작시킵니다. 다만 <b>경고 로그를
 * 남기고</b>, 서버를 재시작하면 임시 키가 바뀌므로 기존 암호문을 읽지
 * 못합니다. 운영에서는 반드시 키를 주입하세요.
 */
@Component
public class SecretCipher {

    private static final Logger log = LoggerFactory.getLogger(SecretCipher.class);

    /** GCM 권장 IV 길이(바이트). 96비트가 표준입니다. */
    private static final int IV_LENGTH = 12;

    /** GCM 인증 태그 길이(비트). */
    private static final int TAG_LENGTH_BITS = 128;

    /** AES-256 키 길이(바이트). */
    private static final int KEY_LENGTH_BYTES = 32;

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    /** Base64 로 인코딩된 256비트 키 (없으면 빈 문자열). */
    private final String configuredKey;

    /** 실제 사용할 키. */
    private SecretKey key;

    /** 임시 키를 썼는지 여부. (운영 경고용) */
    private boolean usingEphemeralKey;

    private final SecureRandom random = new SecureRandom();

    /**
     * @param configuredKey {@code sonar.secret.key} (Base64 인코딩된 32바이트)
     */
    public SecretCipher(@Value("${sonar.secret.key:}") String configuredKey) {
        this.configuredKey = configuredKey;
    }

    /**
     * 키를 준비합니다.
     *
     * <p>설정된 키가 올바르면 그것을 쓰고, 없거나 형식이 틀리면 임시 키를
     * 만들어 경고합니다. 형식 오류를 예외로 던지지 않는 이유는, 키 설정
     * 실수로 <b>서버 전체가 기동하지 못하는 것</b>이 더 나쁘기 때문입니다.
     */
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

    /** @return 임시 키를 쓰고 있으면 {@code true} */
    public boolean isUsingEphemeralKey() {
        return usingEphemeralKey;
    }

    /**
     * 평문을 암호화합니다.
     *
     * @param plaintext 평문 (null/빈 문자열이면 그대로 반환)
     * @return {@code base64(IV || ciphertext || tag)}, 실패 시 {@code null}
     */
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

    /**
     * 암호문을 복호화합니다.
     *
     * <p>변조되었거나 키가 바뀌었으면 예외를 던집니다. 조용히 null 을 돌려주면
     * "인증 실패" 가 "설정 없음" 으로 보여 원인을 찾기 어려워집니다.
     *
     * @param encrypted {@link #encrypt} 결과 (null/빈 문자열이면 그대로 반환)
     * @return 평문
     * @throws IllegalStateException 복호화에 실패한 경우
     */
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

    /**
     * 값이 저장되어 있는지만 확인합니다. 복호화는 하지 않습니다.
     *
     * @param encrypted 암호문
     * @return 값이 있으면 {@code true}
     */
    public boolean isPresent(String encrypted) {
        return encrypted != null && !encrypted.isBlank();
    }
}
