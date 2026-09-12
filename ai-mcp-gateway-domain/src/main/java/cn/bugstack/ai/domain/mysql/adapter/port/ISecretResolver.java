package cn.bugstack.ai.domain.mysql.adapter.port;

/**
 * 运行时密钥解析端口。领域模型只持有密钥引用，不保存或暴露实际密码。
 */
@FunctionalInterface
public interface ISecretResolver {

    /**
     * 根据运行时密钥引用解析秘密值；解析失败时返回 {@code null}。
     */
    String resolve(String reference);
}
