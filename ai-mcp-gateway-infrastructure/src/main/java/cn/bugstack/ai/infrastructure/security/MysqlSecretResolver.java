package cn.bugstack.ai.infrastructure.security;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.function.Function;

/**
 * MySQL 技术密钥解析器。
 *
 * <p>解析器只负责技术端口转发，具体密钥来源由 App Config 通过组合根提供，避免本层直接读取环境
 * 或系统属性。</p>
 */
@Component("mysqlSecretResolver")
public class MysqlSecretResolver implements ISecretResolver {

    @Resource(name = "mysqlSecretLookup")
    private Function<String, String> lookup;

    @Override
    public String resolve(String reference) {
        return reference == null || reference.isBlank() ? null : lookup.apply(reference);
    }
}
