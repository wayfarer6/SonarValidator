package org.sonar.sonarvalidator_backend.Service.secret;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SecretCipherTest {
    private static final String KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "invalid-key!", "YWJj"})
    void invalidKeysFailBeforeSecretsCanBeSaved(String value) {
        assertThrows(IllegalStateException.class, () -> new SecretCipher(value).init());
    }

    @Test
    void persistedSecretDecryptsAfterReinitialization() {
        final var first = new SecretCipher(KEY);
        first.init();
        final var second = new SecretCipher(KEY);
        second.init();
        final String encrypted = first.encrypt("regression-test-secret");
        assertNotEquals(encrypted, first.encrypt("regression-test-secret"));
        assertEquals("regression-test-secret", second.decrypt(encrypted));
        assertFalse(second.isUsingEphemeralKey());
    }
}
