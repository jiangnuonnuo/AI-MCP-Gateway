package cn.bugstack.ai.test.infrastructure.adapter.port;

import cn.bugstack.ai.domain.mysql.adapter.port.IMysqlQueryPort;
import cn.bugstack.ai.domain.mysql.model.command.MysqlQueryCommand;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlParameterType;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlQueryPolicy;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplate;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplateParameter;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplateStatus;
import cn.bugstack.ai.infrastructure.adapter.port.MysqlJdbcGateway;
import cn.bugstack.ai.infrastructure.mysql.MysqlConnectionSettings;
import cn.bugstack.ai.infrastructure.mysql.MysqlConnectionSettingsRegistry;
import cn.bugstack.ai.infrastructure.mysql.MysqlTemplateParameterBinder;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** 由显式只读凭证控制的真实 data_warehouse 查询；未提供凭证时不连接外部数据库。 */
class MysqlJdbcGatewayIntegrationTest {

    @Test
    void executesPersistedReadonlyProtocolAgainstDataWarehouse() {
        String username = System.getenv("DATA_WAREHOUSE_READONLY_USERNAME");
        String password = System.getenv("DATA_WAREHOUSE_READONLY_PASSWORD");
        assumeTrue(username != null && !username.isBlank() && password != null && !password.isBlank());

        MysqlJdbcGateway gateway = new MysqlJdbcGateway();
        ReflectionTestUtils.setField(gateway, "connectionSettingsRegistry",
                (MysqlConnectionSettingsRegistry) ref -> Optional.of(settings(username, password)));
        ReflectionTestUtils.setField(gateway, "parameterBinder", new MysqlTemplateParameterBinder());
        ReflectionTestUtils.setField(gateway, "secretResolver",
                (cn.bugstack.ai.infrastructure.security.ISecretResolver) reference -> null);
        try {
            MysqlTemplate protocol = MysqlTemplate.builder().id("900001").version("1.0.0")
                    .name("queryDataWarehouseOrderSummary").description("order summary")
                    .datasourceRef("data-warehouse")
                    .sql("SELECT c.channel_name, COUNT(*) AS order_count, SUM(o.pay_amount) AS total_amount "
                            + "FROM fact_order o JOIN dim_channel c ON c.channel_id = o.channel_id "
                            + "WHERE o.order_time >= :fromTime AND o.order_time < :toTime "
                            + "GROUP BY c.channel_name ORDER BY total_amount DESC")
                    .parameters(List.of(
                            MysqlTemplateParameter.builder().name("fromTime").type(MysqlParameterType.STRING).required(true).build(),
                            MysqlTemplateParameter.builder().name("toTime").type(MysqlParameterType.STRING).required(true).build()))
                    .status(MysqlTemplateStatus.ENABLED).policy(MysqlQueryPolicy.defaults()).build();

            var result = gateway.execute(MysqlQueryCommand.builder().template(protocol)
                    .parameters(Map.of("fromTime", "2024-01-01", "toTime", "2025-01-01"))
                    .queryId("real-mysql-integration").build());

            assertNotNull(result.getColumns());
            assertFalse(result.getColumns().isEmpty());
            assertNotNull(result.getRows());
        } finally {
            gateway.close();
        }
    }

    private static MysqlConnectionSettings settings(String username, String password) {
        return new MysqlConnectionSettings() {
            @Override public String getId() { return "data-warehouse"; }
            @Override public String getJdbcUrl() {
                return System.getenv().getOrDefault("DATA_WAREHOUSE_JDBC_URL",
                        "jdbc:mysql://127.0.0.1:3306/data_warehouse?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false");
            }
            @Override public String getUsername() { return username; }
            @Override public String getPasswordSecretRef() { return null; }
            @Override public String getRuntimePassword() { return password; }
            @Override public int getMaxPoolSize() { return 2; }
            @Override public long getConnectionTimeoutMs() { return 3000; }
            @Override public long getValidationTimeoutMs() { return 1000; }
            @Override public int getMaxConcurrentQueries() { return 2; }
            @Override public long getMaxSqlLength() { return 64 * 1024; }
            @Override public int getMaxRows() { return 100; }
            @Override public long getMaxResultBytes() { return 1024 * 1024; }
            @Override public int getMaxColumns() { return 8; }
            @Override public long getMaxQueryTimeoutMs() { return 5000; }
        };
    }
}
