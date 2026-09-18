package cn.bugstack.ai.test.infrastructure.security;

import cn.bugstack.ai.infrastructure.security.DataSourceCredentialCipher;
import cn.bugstack.ai.types.exception.MysqlQueryException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DataSourceCredentialCipherTest {

    @Test
    void encryptsAndDecryptsWithoutPersistingPlaintext() {
        DataSourceCredentialCipher cipher = cipher();

        DataSourceCredentialCipher.EncryptedValue encrypted = cipher.encrypt("warehouse-password", "key-v1");

        assertFalse(encrypted.ciphertext().contains("warehouse-password"));
        assertEquals("warehouse-password",
                cipher.decrypt(encrypted.ciphertext(), encrypted.nonce(), "key-v1"));
    }

    @Test
    void returnsSanitizedErrorForInvalidCiphertext() {
        DataSourceCredentialCipher cipher = cipher();

        MysqlQueryException error = assertThrows(MysqlQueryException.class,
                () -> cipher.decrypt("not-base64", "not-base64", "key-v1"));

        assertEquals("DATASOURCE_CREDENTIAL_ERROR", error.getCode());
        assertFalse(error.getMessage().contains("not-base64"));
    }

    private static DataSourceCredentialCipher cipher() {
        DataSourceCredentialCipher cipher = new DataSourceCredentialCipher();
        ReflectionTestUtils.setField(cipher, "secretResolver",
                (cn.bugstack.ai.infrastructure.security.ISecretResolver) reference -> "test-master-key");
        return cipher;
    }
}
