package cn.bugstack.ai.test.infrastructure.adapter.port;

import cn.bugstack.ai.config.MysqlConnectionProperties;
import cn.bugstack.ai.config.MysqlDataSourceConfig;
import cn.bugstack.ai.config.MysqlMvpTemplateFactory;
import cn.bugstack.ai.domain.mysql.model.command.MysqlQueryCommand;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlQueryResult;
import cn.bugstack.ai.infrastructure.adapter.port.MysqlJdbcGateway;
import cn.bugstack.ai.infrastructure.mysql.MysqlTemplateParameterBinder;
import cn.bugstack.ai.infrastructure.security.ISecretResolver;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 使用运行时凭证验证本机数仓的真实只读查询；未注入凭证时跳过，避免测试环境误连数据库。
 */
class MysqlJdbcGatewayIntegrationTest {

    @Test
    void executesPublishedTemplateAgainstDataWarehouse() {
        String username = System.getenv("WAREHOUSE_MYSQL_USERNAME");
        String password = System.getenv("WAREHOUSE_MYSQL_PASSWORD");
        assumeTrue(username != null && !username.isBlank());
        assumeTrue(password != null && !password.isBlank());

        MysqlDataSourceConfig config = MysqlDataSourceConfig.builder()
                .id(MysqlMvpTemplateFactory.DATASOURCE_REF)
                .jdbcUrl(System.getenv().getOrDefault("WAREHOUSE_MYSQL_URL",
                        "jdbc:mysql://127.0.0.1:3306/data_warehouse?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false"))
                .username(username)
                .passwordSecretRef("env:WAREHOUSE_MYSQL_PASSWORD")
                .build();
        MysqlConnectionProperties properties = MysqlConnectionProperties.builder()
                .datasource(Map.of(MysqlMvpTemplateFactory.DATASOURCE_REF, config))
                .build();

        MysqlJdbcGateway gateway = new MysqlJdbcGateway();
        ReflectionTestUtils.setField(gateway, "connectionSettingsRegistry", properties);
        ReflectionTestUtils.setField(gateway, "secretResolver",
                (ISecretResolver) reference -> System.getenv(reference.substring("env:".length())));
        ReflectionTestUtils.setField(gateway, "parameterBinder", new MysqlTemplateParameterBinder());
        try {
            MysqlQueryResult result = gateway.execute(new MysqlQueryCommand(
                    MysqlMvpTemplateFactory.publishedTemplate(),
                    Map.of("fromTime", "2024-01-01 00:00:00",
                            "toTime", "2025-01-01 00:00:00",
                            "orderStatus", "1"),
                    null,
                    "integration-test"));
            assertNotNull(result.getColumns());
            assertFalse(result.getColumns().isEmpty());
            assertNotNull(result.getRows());
        } finally {
            gateway.close();
        }
    }
}
