package cn.bugstack.ai.config;

import cn.bugstack.ai.domain.mysql.adapter.port.IMysqlQueryPort;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlQueryPolicy;
import cn.bugstack.ai.infrastructure.adapter.port.MysqlJdbcGateway;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Function;

/**
 * 组装业务数仓 MySQL 执行链的应用配置。
 */
@Configuration
@EnableConfigurationProperties(MysqlConnectionProperties.class)
public class MysqlExecutionConfig {

    /** 创建由 Spring 管理的 MySQL JDBC 技术适配器。 */
    @Bean(destroyMethod = "close")
    public MysqlJdbcGateway mysqlJdbcGateway() {
        return new MysqlJdbcGateway();
    }

    /** 将 JDBC 适配器暴露为 Domain 查询端口。 */
    @Bean
    public IMysqlQueryPort mysqlQueryPort(MysqlJdbcGateway gateway) {
        return gateway;
    }

    /**
     * 暴露 App Config 的技术上限，供持久化数据源/协议适配器在 Domain 编排时合并。
     * 该 Bean 不包含 JDBC 地址、用户名、凭证或业务数据源状态。
     */
    @Bean("mysqlTechnicalPolicy")
    public MysqlQueryPolicy mysqlTechnicalPolicy(MysqlConnectionProperties properties) {
        return properties.technicalPolicy();
    }

    /** 为 Infrastructure 技术密钥解析器提供组合根查找函数。 */
    @Bean("mysqlSecretLookup")
    public Function<String, String> mysqlSecretLookup() {
        return MysqlExecutionConfig::resolveSecret;
    }

    /** 在组合根解析密钥引用，避免技术适配器直接读取进程环境。 */
    private static String resolveSecret(String reference) {
        if (reference == null || reference.isBlank()) return null;
        if (reference.startsWith("env:")) return System.getenv(reference.substring("env:".length()));
        if (reference.startsWith("sys:")) return System.getProperty(reference.substring("sys:".length()));
        return System.getenv(reference);
    }
}
