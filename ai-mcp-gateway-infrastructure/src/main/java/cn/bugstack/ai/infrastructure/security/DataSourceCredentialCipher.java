package cn.bugstack.ai.infrastructure.security;

import cn.bugstack.ai.types.exception.MysqlQueryException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * mcp_datasource 凭证密文适配器。
 *
 * <p>控制库只保存密文和 nonce；主密钥通过 App Config 提供的解析器读取，任何解密失败都转换为
 * 不包含驱动细节的稳定错误。</p>
 */
@Component
public class DataSourceCredentialCipher {

    private static final int GCM_TAG_BITS = 128;
    private static final int NONCE_BYTES = 12;

    @Resource(name = "mysqlSecretResolver")
    private ISecretResolver secretResolver;

    /** 解密单条数据源密码。 */
    public String decrypt(String ciphertext, String nonce, String keyRef) {
        if (ciphertext == null || ciphertext.isBlank() || nonce == null || nonce.isBlank()
                || keyRef == null || keyRef.isBlank()) {
            throw new MysqlQueryException("DATASOURCE_CREDENTIAL_ERROR", "data source credential is unavailable");
        }
        String keyMaterial = secretResolver.resolve(keyRef);
        if (keyMaterial == null || keyMaterial.isBlank()) {
            throw new MysqlQueryException("DATASOURCE_CREDENTIAL_ERROR", "data source credential is unavailable");
        }
        try {
            byte[] key = digestKey(keyMaterial);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, Base64.getDecoder().decode(nonce)));
            byte[] plain = cipher.doFinal(Base64.getDecoder().decode(ciphertext));
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new MysqlQueryException("DATASOURCE_CREDENTIAL_ERROR", "data source credential is unavailable", e);
        }
    }

    /** 为管理适配器提供加密能力；返回值仍只包含密文和 nonce。 */
    public EncryptedValue encrypt(String plaintext, String keyRef) {
        if (plaintext == null || keyRef == null || keyRef.isBlank()) {
            throw new IllegalArgumentException("credential and key reference are required");
        }
        String keyMaterial = secretResolver.resolve(keyRef);
        if (keyMaterial == null || keyMaterial.isBlank()) {
            throw new IllegalArgumentException("credential key is unavailable");
        }
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            new SecureRandom().nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(digestKey(keyMaterial), "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, nonce));
            return new EncryptedValue(
                    Base64.getEncoder().encodeToString(cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8))),
                    Base64.getEncoder().encodeToString(nonce));
        } catch (Exception e) {
            throw new IllegalStateException("data source credential encryption failed", e);
        }
    }

    private static byte[] digestKey(String keyMaterial) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(keyMaterial.getBytes(StandardCharsets.UTF_8));
    }

    public record EncryptedValue(String ciphertext, String nonce) {
    }
}
