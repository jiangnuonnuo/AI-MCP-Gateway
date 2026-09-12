package cn.bugstack.ai.infrastructure.config;

import cn.bugstack.ai.domain.mysql.model.valobj.MysqlDataSourceConfig;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlDataSourceStatus;
import cn.bugstack.ai.infrastructure.adapter.port.InMemoryMysqlDataSourceRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 读取 MySQL 数仓的运行时配置。未注入用户名时不注册连接，避免启动过程误连或复用控制库。
 */
@Component
public class MysqlMvpDataSourceInitializer {

    private final InMemoryMysqlDataSourceRegistry registry;
    private final Environment environment;

    public MysqlMvpDataSourceInitializer(InMemoryMysqlDataSourceRegistry registry, Environment environment) {
        this.registry = registry;
        this.environment = environment;
    }

    @PostConstruct
    public void registerRuntimeDataSource() {
        String username = firstNonBlank(environment.getProperty("gateway.mysql.datasource.warehouse-test.username"),
                runtimeValue("WAREHOUSE_MYSQL_USERNAME", null));
        if (username == null || username.isBlank()) {
            return;
        }
        String url = firstNonBlank(environment.getProperty("gateway.mysql.datasource.warehouse-test.url"),
                runtimeValue("WAREHOUSE_MYSQL_URL",
                        "jdbc:mysql://127.0.0.1:3306/data_warehouse?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false"));
        MysqlDataSourceConfig.Builder builder = MysqlDataSourceConfig.builder()
                .id(MysqlMvpTemplateFactory.DATASOURCE_REF)
                .jdbcUrl(url)
                .username(username)
                .status(MysqlDataSourceStatus.ENABLED)
                .maxPoolSize(5)
                .connectionTimeoutMs(5_000)
                .validationTimeoutMs(2_000)
                .maxConcurrentQueries(4)
                .maxRows(1_000)
                .maxResultBytes(4 * 1024 * 1024)
                .maxQueryTimeoutMs(30_000);
        builder.passwordSecretRef("env:WAREHOUSE_MYSQL_PASSWORD");
        registry.register(builder.build());
    }

    private static String runtimeValue(String name, String fallback) {
        String systemValue = System.getProperty(name);
        if (systemValue != null && !systemValue.isBlank()) return systemValue;
        String envValue = System.getenv(name);
        return envValue == null || envValue.isBlank() ? fallback : envValue;
    }

    private static String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
