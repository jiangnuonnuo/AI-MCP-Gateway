package cn.bugstack.ai.infrastructure.security;

/**
 * Infrastructure 运行时密钥解析能力。
 */
@FunctionalInterface
public interface ISecretResolver {

    /**
     * 根据外部配置中的密钥引用解析秘密值。
     *
     * @param reference 密钥引用
     * @return 运行时秘密值，无法解析时返回 {@code null}
     */
    String resolve(String reference);
}
