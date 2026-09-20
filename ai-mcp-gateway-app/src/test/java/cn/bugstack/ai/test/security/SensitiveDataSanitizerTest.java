package cn.bugstack.ai.test.security;

import cn.bugstack.ai.types.security.SensitiveDataSanitizer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SensitiveDataSanitizerTest {

    @Test
    void masksCredentialFieldsAndJdbcParameters() {
        Map<String, Object> sanitized = SensitiveDataSanitizer.sanitizeMap(Map.of(
                "password", "secret",
                "password_ciphertext", "cipher",
                "nonce", "nonce-value",
                "Authorization", "Bearer token",
                "jdbcUrl", "jdbc:mysql://db/app?user=alice&password=secret"));

        assertEquals("***", sanitized.get("password"));
        assertEquals("***", sanitized.get("password_ciphertext"));
        assertEquals("***", sanitized.get("nonce"));
        assertEquals("***", sanitized.get("Authorization"));
        assertFalse(String.valueOf(sanitized.get("jdbcUrl")).contains("secret"));
    }
}
