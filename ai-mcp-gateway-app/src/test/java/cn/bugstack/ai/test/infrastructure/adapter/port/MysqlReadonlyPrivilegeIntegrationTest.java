package cn.bugstack.ai.test.infrastructure.adapter.port;

import cn.bugstack.ai.domain.mysql.model.valobj.MysqlQueryPolicy;
import cn.bugstack.ai.domain.mysql.model.valobj.SqlSafetyDecision;
import cn.bugstack.ai.domain.mysql.service.safety.MysqlSqlSafetyChain;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 验证应用层和 MySQL 权限层共同阻断写操作。
 *
 * <p>测试账号必须由运行环境提供，并且只授予 {@code data_warehouse.*} 的 SELECT 权限；
 * 测试不会执行任何会修改数据的语句。</p>
 */
class MysqlReadonlyPrivilegeIntegrationTest {

    private static final String WRITE_SQL =
            "UPDATE data_warehouse.fact_order SET order_status = order_status WHERE 1 = 0";

    @Test
    void blocksWriteAtApplicationAndDatabasePrivilegeLayers() throws Exception {
        String username = System.getenv("WAREHOUSE_MYSQL_READONLY_USERNAME");
        String password = System.getenv("WAREHOUSE_MYSQL_READONLY_PASSWORD");
        assumeTrue(username != null && !username.isBlank());
        assumeTrue(password != null && !password.isBlank());

        String jdbcUrl = System.getenv().getOrDefault("WAREHOUSE_MYSQL_READONLY_URL",
                System.getenv().getOrDefault("WAREHOUSE_MYSQL_URL",
                        "jdbc:mysql://127.0.0.1:3306/data_warehouse?useUnicode=true"
                                + "&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false"));

        MysqlSqlSafetyChain safetyChain = MysqlSqlSafetyChain.defaultChain();
        ReflectionTestUtils.setField(safetyChain, "analysisPort",
                new cn.bugstack.ai.infrastructure.adapter.port.MysqlSqlParser());
        SqlSafetyDecision decision = safetyChain.validate(WRITE_SQL, java.util.Map.of(),
                MysqlQueryPolicy.defaults());
        assertFalse(decision.isAllowed(), "应用层必须拒绝写操作");

        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
            List<String> grants = readCurrentUserGrants(connection);
            String targetGrant = grants.stream()
                    .filter(grant -> grant.toUpperCase(Locale.ROOT).contains("DATA_WAREHOUSE"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("missing data_warehouse grant"));
            String normalizedGrant = targetGrant.toUpperCase(Locale.ROOT);
            assertTrue(normalizedGrant.contains("SELECT"),
                    "只读账号必须拥有 data_warehouse 的 SELECT 权限");
            assertFalse(normalizedGrant.matches(".*\\b(INSERT|UPDATE|DELETE|CREATE|ALTER|DROP|TRUNCATE)\\b.*"),
                    "data_warehouse 授权不得包含写权限");

            SQLException exception = assertThrows(SQLException.class, () -> {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate(WRITE_SQL);
                }
            });
            assertTrue(exception.getErrorCode() == 1142 || "42000".equals(exception.getSQLState()),
                    "数据库层应以权限错误阻断写操作");
        }
    }

    private List<String> readCurrentUserGrants(Connection connection) throws SQLException {
        List<String> grants = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SHOW GRANTS FOR CURRENT_USER")) {
            while (resultSet.next()) {
                grants.add(resultSet.getString(1));
            }
        }
        return grants;
    }
}
