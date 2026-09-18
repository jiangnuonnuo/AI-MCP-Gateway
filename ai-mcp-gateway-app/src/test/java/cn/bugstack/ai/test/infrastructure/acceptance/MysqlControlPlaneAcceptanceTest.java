package cn.bugstack.ai.test.infrastructure.acceptance;

import cn.bugstack.ai.domain.mysql.model.valobj.MysqlQueryPolicy;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplate;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplateStatus;
import cn.bugstack.ai.domain.mysql.service.MysqlProtocolManagementService;
import cn.bugstack.ai.domain.session.model.valobj.McpSchemaVO;
import cn.bugstack.ai.domain.session.model.valobj.SessionSyncInfoVO;
import cn.bugstack.ai.domain.session.service.message.handler.impl.ToolsCallHandler;
import cn.bugstack.ai.domain.session.service.message.handler.impl.ToolsListHandler;
import cn.bugstack.ai.infrastructure.redis.IRedisService;
import cn.bugstack.ai.types.exception.MysqlDomainException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.Mockito;
import org.redisson.api.RMap;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/** 仅在 Xerina 显式打开真实验收开关时运行，连接本地控制库和 data_warehouse。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.main.allow-bean-definition-overriding=true")
@Import(MysqlControlPlaneAcceptanceTest.RedisTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "OPENSPEC_REAL_MYSQL_ACCEPTANCE", matches = "1")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MysqlControlPlaneAcceptanceTest {

    private static final String GATEWAY_ID = "gateway_001";
    private static final String BASE_TOOL_NAME = "queryDataWarehouseOrderSummary";
    private static final long BASE_PROTOCOL_ID = 900001L;
    private static final long TEMP_ID_MIN = 910000L;
    private static final long TEMP_ID_MAX = 929999L;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private ToolsListHandler toolsListHandler;

    @Autowired
    private ToolsCallHandler toolsCallHandler;

    @Autowired
    private MysqlProtocolManagementService protocolManagementService;

    @Autowired
    private JdbcTemplate controlJdbcTemplate;

    @TestConfiguration(proxyBeanMethods = false)
    static class RedisTestConfiguration {

        @Bean
        RedissonClient redissonClient() {
            return Mockito.mock(RedissonClient.class);
        }

        @Bean
        IRedisService redisService() {
            IRedisService redisService = Mockito.mock(IRedisService.class);
            RMap<String, SessionSyncInfoVO> sessionMap = Mockito.mock(RMap.class);
            RTopic topic = Mockito.mock(RTopic.class);
            when(redisService.<String, SessionSyncInfoVO>getMap(anyString())).thenReturn(sessionMap);
            when(sessionMap.readAllValues()).thenReturn(java.util.Collections.emptyList());
            when(redisService.getTopic(anyString())).thenReturn(topic);
            return redisService;
        }
    }

    @BeforeEach
    void restoreAcceptanceFixture() {
        cleanTemporaryFixtures();
    }

    @AfterEach
    void cleanUpAcceptanceFixture() {
        cleanTemporaryFixtures();
    }

    @Test
    @Order(1)
    void listsAndCallsEnabledPersistedMysqlTool() throws Exception {
        Map<?, ?> listResult = listTools();
        List<?> tools = (List<?>) listResult.get("tools");
        String listedJson = OBJECT_MAPPER.writeValueAsString(listResult);

        assertTrue(tools.stream().anyMatch(tool -> tool.toString().contains(BASE_TOOL_NAME)));
        assertFalse(listedJson.contains("SELECT"));
        assertFalse(listedJson.contains("data-warehouse"));
        assertFalse(listedJson.contains(String.valueOf(BASE_PROTOCOL_ID)));
        assertFalse(listedJson.contains("jdbc"));

        Map<?, ?> callResult = callTool(BASE_TOOL_NAME,
                Map.of("fromTime", "2024-01-01", "toTime", "2025-01-01"));

        assertEquals(Boolean.FALSE, callResult.get("isError"), callResult::toString);
        int rowCount = ((Number) callResult.get("rowCount")).intValue();
        List<?> columns = (List<?>) callResult.get("columns");
        assertTrue(rowCount > 0 && rowCount <= 100);
        assertTrue(columns.size() >= 2 && columns.size() <= 8);
        assertFalse(Boolean.TRUE.equals(callResult.get("truncated")));
        assertEquals(rowCount, ((List<?>) callResult.get("rows")).size());
        assertFalse(callResult.toString().contains("jdbc:mysql://"));
        assertFalse(callResult.toString().contains("password"));
        assertEquals(1L, controlJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mcp_protocol_mysql WHERE protocol_id = ? AND status = 1",
                Long.class, BASE_PROTOCOL_ID));

        Map<?, ?> overrideAttempt = callTool(BASE_TOOL_NAME,
                Map.of("fromTime", "2024-01-01", "toTime", "2025-01-01",
                        "sql", "SELECT 1", "datasourceRef", "other"));
        assertEquals(Boolean.TRUE, overrideAttempt.get("isError"));
        assertEquals("SQL_PARAMETER_ERROR", overrideAttempt.get("errorCode"));
    }

    @Test
    @Order(2)
    void returnsSanitizedErrorsForRuntimeFailureMatrix() throws Exception {
        controlJdbcTemplate.update("UPDATE mcp_datasource SET status = 0 WHERE datasource_ref = 'data-warehouse'");
        Map<?, ?> disabled = callTool(BASE_TOOL_NAME,
                Map.of("fromTime", "2024-01-01", "toTime", "2025-01-01"));
        assertSafeFailure(disabled, Set.of("DATASOURCE_UNAVAILABLE"));
        controlJdbcTemplate.update("UPDATE mcp_datasource SET status = 1 WHERE datasource_ref = 'data-warehouse'");

        insertProtocolAndTool(910001L, "acceptanceMissingTable", "data-warehouse",
                "SELECT 1 FROM acceptance_missing_table");
        assertSafeFailure(callTool("acceptanceMissingTable", Map.of()), Set.of("BACKEND_ERROR"));

        insertProtocolAndTool(910002L, "acceptanceMissingColumn", "data-warehouse",
                "SELECT acceptance_missing_column FROM fact_order LIMIT 1");
        assertSafeFailure(callTool("acceptanceMissingColumn", Map.of()), Set.of("BACKEND_ERROR"));

        Map<String, Object> baseDataSource = baseDataSource();
        String deniedRef = "acceptance-denied";
        String deniedUsername = "acceptance_denied_" + Long.toUnsignedString(System.nanoTime(), 36);
        insertDataSource(deniedRef, (String) baseDataSource.get("jdbc_url"), deniedUsername, baseDataSource);
        insertProtocolAndTool(910003L, "acceptanceDenied", deniedRef, "SELECT 1");
        assertSafeFailure(callTool("acceptanceDenied", Map.of()),
                Set.of("DATASOURCE_UNAVAILABLE", "BACKEND_ERROR"));

        String unavailableRef = "acceptance-network";
        String unavailableUrl = ((String) baseDataSource.get("jdbc_url"))
                .replace("127.0.0.1:3306", "127.0.0.1:1")
                .replace("localhost:3306", "127.0.0.1:1");
        insertDataSource(unavailableRef, unavailableUrl, (String) baseDataSource.get("username"), baseDataSource);
        insertProtocolAndTool(910004L, "acceptanceNetworkFailure", unavailableRef, "SELECT 1");
        assertSafeFailure(callTool("acceptanceNetworkFailure", Map.of()),
                Set.of("DATASOURCE_UNAVAILABLE", "BACKEND_ERROR"));
    }

    @Test
    @Order(3)
    void rejectsUnsafeProtocolsBeforeControlPlanePersistence() {
        Map<String, String> rejectedSql = new LinkedHashMap<>();
        rejectedSql.put("malformed", "SELECT FROM");
        rejectedSql.put("injection", "SELECT 1; DROP TABLE fact_order");
        rejectedSql.put("multi-statement", "SELECT 1; SELECT 2");
        rejectedSql.put("write", "DELETE FROM fact_order");
        rejectedSql.put("ddl", "DROP TABLE fact_order");
        rejectedSql.put("dcl", "GRANT SELECT ON data_warehouse.* TO 'acceptance'@'%'");
        rejectedSql.put("dangerous-function", "SELECT SLEEP(1)");
        rejectedSql.put("parameter-mismatch", "SELECT * FROM fact_order WHERE order_id = :orderId");

        long protocolId = 920000L;
        for (Map.Entry<String, String> entry : rejectedSql.entrySet()) {
            MysqlTemplate protocol = MysqlTemplate.builder()
                    .id(String.valueOf(protocolId++))
                    .version("1.0.0")
                    .name(entry.getKey())
                    .description("Acceptance-only rejected protocol")
                    .datasourceRef("data-warehouse")
                    .sql(entry.getValue())
                    .parameters(List.of())
                    .status(MysqlTemplateStatus.DISABLED)
                    .policy(MysqlQueryPolicy.defaults())
                    .build();

            MysqlDomainException error = assertThrows(MysqlDomainException.class,
                    () -> protocolManagementService.save(protocol), entry.getKey());
            assertTrue(Set.of("SQL_POLICY_REJECTED", "SQL_SYNTAX_ERROR", "SQL_SAFETY_FAILED")
                    .contains(error.getCode()), entry.getKey() + ": " + error.getCode());
        }

        assertEquals(0L, controlJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mcp_protocol_mysql WHERE protocol_id BETWEEN ? AND ?",
                Long.class, 920000L, 920099L));
        assertEquals(50000L, targetTableCount("fact_order"));
        assertEquals(8L, targetTableCount("dim_channel"));
    }

    @Test
    @Order(4)
    void bindsInjectionPayloadAsDataInsteadOfSql() {
        insertProtocolAndTool(910005L, "acceptanceBoundValue", "data-warehouse",
                "SELECT :value AS bound_value");
        controlJdbcTemplate.update("""
                INSERT INTO mcp_protocol_mapping
                  (protocol_id, protocol_type, mapping_type, parent_path, field_name, mcp_path,
                   mcp_type, mcp_desc, is_required, sort_order)
                VALUES (?, 'mysql', 'request', NULL, 'value', 'value', 'string',
                        'Acceptance bound value', 1, 1)
                """, 910005L);
        String payload = "x'; DROP TABLE fact_order; --";

        Map<?, ?> result = callTool("acceptanceBoundValue", Map.of("value", payload));

        assertEquals(Boolean.FALSE, result.get("isError"), result::toString);
        assertEquals(1, ((Number) result.get("rowCount")).intValue());
        List<?> firstRow = (List<?>) ((List<?>) result.get("rows")).get(0);
        assertEquals(payload, firstRow.get(0));
        assertEquals(50000L, targetTableCount("fact_order"));
    }

    @Test
    @Order(5)
    void appliesTwoStateLifecycleAndRebindsWithANewProtocolId() throws Exception {
        controlJdbcTemplate.update("UPDATE mcp_gateway_tool SET status = 0 WHERE gateway_id = ? AND tool_name = ?",
                GATEWAY_ID, BASE_TOOL_NAME);
        assertToolUnavailableFromList(BASE_TOOL_NAME);
        assertSafeFailure(callTool(BASE_TOOL_NAME, Map.of()), Set.of("TOOL_DISABLED"));
        controlJdbcTemplate.update("UPDATE mcp_gateway_tool SET status = 1 WHERE gateway_id = ? AND tool_name = ?",
                GATEWAY_ID, BASE_TOOL_NAME);

        controlJdbcTemplate.update("UPDATE mcp_protocol_mysql SET status = 0 WHERE protocol_id = ?", BASE_PROTOCOL_ID);
        assertToolUnavailableFromList(BASE_TOOL_NAME);
        assertSafeFailure(callTool(BASE_TOOL_NAME, Map.of()), Set.of("TOOL_DISABLED"));
        controlJdbcTemplate.update("UPDATE mcp_protocol_mysql SET status = 1 WHERE protocol_id = ?", BASE_PROTOCOL_ID);

        controlJdbcTemplate.update("UPDATE mcp_datasource SET status = 0 WHERE datasource_ref = 'data-warehouse'");
        assertToolUnavailableFromList(BASE_TOOL_NAME);
        assertSafeFailure(callTool(BASE_TOOL_NAME, Map.of()), Set.of("DATASOURCE_UNAVAILABLE"));
        controlJdbcTemplate.update("UPDATE mcp_datasource SET status = 1 WHERE datasource_ref = 'data-warehouse'");

        String originalSql = controlJdbcTemplate.queryForObject(
                "SELECT sql_text FROM mcp_protocol_mysql WHERE protocol_id = ?", String.class, BASE_PROTOCOL_ID);
        insertProtocol(910006L, "data-warehouse", "SELECT 1 AS replacement_value");
        controlJdbcTemplate.update("UPDATE mcp_gateway_tool SET protocol_id = ? WHERE gateway_id = ? AND tool_name = ?",
                910006L, GATEWAY_ID, BASE_TOOL_NAME);

        Map<?, ?> rebound = callTool(BASE_TOOL_NAME, Map.of());

        assertEquals(Boolean.FALSE, rebound.get("isError"), rebound::toString);
        assertEquals(910006L, controlJdbcTemplate.queryForObject(
                "SELECT protocol_id FROM mcp_gateway_tool WHERE gateway_id = ? AND tool_name = ?",
                Long.class, GATEWAY_ID, BASE_TOOL_NAME));
        assertEquals(originalSql, controlJdbcTemplate.queryForObject(
                "SELECT sql_text FROM mcp_protocol_mysql WHERE protocol_id = ?", String.class, BASE_PROTOCOL_ID));
        assertEquals(1L, controlJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mcp_protocol_mysql WHERE protocol_id = ? AND status = 1",
                Long.class, BASE_PROTOCOL_ID));
    }

    @Test
    @Order(6)
    void keepsRuntimeSecretsOutOfPersistenceResponsesAndLogs() throws Exception {
        String controlPassword = requiredEnvironment("SPRING_DATASOURCE_PASSWORD");
        String credentialKey = requiredEnvironment("DATA_WAREHOUSE_CREDENTIAL_KEY");
        String jdbcUrl = controlJdbcTemplate.queryForObject(
                "SELECT jdbc_url FROM mcp_datasource WHERE datasource_ref = 'data-warehouse'", String.class);

        assertEquals(0L, controlJdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM mcp_datasource
                WHERE password_ciphertext IN (?, ?)
                   OR password_nonce IN (?, ?)
                   OR encryption_key_ref IN (?, ?)
                """, Long.class, controlPassword, credentialKey, controlPassword, credentialKey,
                controlPassword, credentialKey));
        assertEquals(0L, controlJdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM mcp_datasource
                WHERE LOWER(jdbc_url) LIKE '%password=%'
                   OR LOWER(jdbc_url) LIKE '%authorization=%'
                """, Long.class));

        Map<?, ?> response = callTool(BASE_TOOL_NAME,
                Map.of("fromTime", "2024-01-01", "toTime", "2025-01-01"));
        String responseJson = OBJECT_MAPPER.writeValueAsString(response);
        assertDoesNotContain(responseJson, controlPassword, credentialKey, jdbcUrl, "authorization");

        String logs = currentAcceptanceLogs();
        assertDoesNotContain(logs, controlPassword, credentialKey, jdbcUrl, "authorization: bearer");
    }

    private Map<?, ?> listTools() {
        return (Map<?, ?>) toolsListHandler.handle(GATEWAY_ID,
                new McpSchemaVO.JSONRPCRequest("2.0", "tools/list", "list-real", Map.of())).result();
    }

    private Map<?, ?> callTool(String toolName, Map<String, ?> arguments) {
        return (Map<?, ?>) toolsCallHandler.handle(GATEWAY_ID,
                new McpSchemaVO.JSONRPCRequest("2.0", "tools/call", "acceptance-call",
                        Map.of("name", toolName, "arguments", arguments))).result();
    }

    private void assertToolUnavailableFromList(String toolName) throws Exception {
        String json = OBJECT_MAPPER.writeValueAsString(listTools());
        assertFalse(json.contains(toolName), json);
    }

    private void assertSafeFailure(Map<?, ?> result, Set<String> expectedCodes) throws Exception {
        assertEquals(Boolean.TRUE, result.get("isError"), result::toString);
        assertTrue(expectedCodes.contains(String.valueOf(result.get("errorCode"))), result::toString);
        String json = OBJECT_MAPPER.writeValueAsString(result).toLowerCase(Locale.ROOT);
        assertDoesNotContain(json, "jdbc:mysql://", "password", "authorization", "access denied",
                "data_warehouse", "fact_order", "acceptance_missing", "root", "select ");
    }

    private void insertProtocolAndTool(long protocolId, String toolName, String datasourceRef, String sql) {
        insertProtocol(protocolId, datasourceRef, sql);
        controlJdbcTemplate.update("""
                INSERT INTO mcp_gateway_tool
                  (gateway_id, tool_id, tool_name, tool_type, tool_description, tool_version,
                   protocol_id, protocol_type, status)
                VALUES (?, ?, ?, 'function', 'Acceptance-only MySQL tool', '1.0.0', ?, 'mysql', 1)
                """, GATEWAY_ID, protocolId, toolName, protocolId);
    }

    private void insertProtocol(long protocolId, String datasourceRef, String sql) {
        Long datasourceId = controlJdbcTemplate.queryForObject(
                "SELECT id FROM mcp_datasource WHERE datasource_ref = ?", Long.class, datasourceRef);
        controlJdbcTemplate.update("""
                INSERT INTO mcp_protocol_mysql
                  (protocol_id, datasource_id, sql_text, max_rows, max_result_bytes, max_columns, timeout_ms, status)
                VALUES (?, ?, ?, 100, 1048576, 8, 3000, 1)
                """, protocolId, datasourceId, sql);
    }

    private Map<String, Object> baseDataSource() {
        return controlJdbcTemplate.queryForMap("""
                SELECT jdbc_url, username, password_ciphertext, password_nonce, encryption_key_ref
                FROM mcp_datasource WHERE datasource_ref = 'data-warehouse'
                """);
    }

    private void insertDataSource(String datasourceRef, String jdbcUrl, String username,
                                  Map<String, Object> encryptedCredential) {
        controlJdbcTemplate.update("""
                INSERT INTO mcp_datasource
                  (datasource_ref, datasource_name, datasource_type, jdbc_url, username,
                   password_ciphertext, password_nonce, encryption_key_ref, status)
                VALUES (?, 'Acceptance-only data source', 'mysql', ?, ?, ?, ?, ?, 1)
                """, datasourceRef, jdbcUrl, username, encryptedCredential.get("password_ciphertext"),
                encryptedCredential.get("password_nonce"), encryptedCredential.get("encryption_key_ref"));
    }

    private long targetTableCount(String tableName) {
        assertTrue(Set.of("fact_order", "dim_channel").contains(tableName));
        Map<String, Object> baseDataSource = baseDataSource();
        String jdbcUrl = (String) baseDataSource.get("jdbc_url");
        String username = (String) baseDataSource.get("username");
        String password = requiredEnvironment("SPRING_DATASOURCE_PASSWORD");
        try (java.sql.Connection connection = java.sql.DriverManager.getConnection(jdbcUrl, username, password);
             java.sql.PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM " + tableName);
             java.sql.ResultSet resultSet = statement.executeQuery()) {
            assertTrue(resultSet.next());
            return resultSet.getLong(1);
        } catch (java.sql.SQLException e) {
            throw new AssertionError("Target table count verification failed", e);
        }
    }

    private void cleanTemporaryFixtures() {
        controlJdbcTemplate.update("""
                UPDATE mcp_gateway_tool SET protocol_id = ?, status = 1
                WHERE gateway_id = ? AND tool_name = ?
                """, BASE_PROTOCOL_ID, GATEWAY_ID, BASE_TOOL_NAME);
        controlJdbcTemplate.update("UPDATE mcp_protocol_mysql SET status = 1 WHERE protocol_id = ?", BASE_PROTOCOL_ID);
        controlJdbcTemplate.update("UPDATE mcp_datasource SET status = 1 WHERE datasource_ref = 'data-warehouse'");
        controlJdbcTemplate.update("""
                DELETE FROM mcp_gateway_tool
                WHERE gateway_id = ? AND tool_id BETWEEN ? AND ?
                """, GATEWAY_ID, TEMP_ID_MIN, TEMP_ID_MAX);
        controlJdbcTemplate.update("""
                DELETE FROM mcp_protocol_mapping
                WHERE protocol_type = 'mysql' AND protocol_id BETWEEN ? AND ?
                """, TEMP_ID_MIN, TEMP_ID_MAX);
        controlJdbcTemplate.update("DELETE FROM mcp_protocol_mysql WHERE protocol_id BETWEEN ? AND ?",
                TEMP_ID_MIN, TEMP_ID_MAX);
        controlJdbcTemplate.update("DELETE FROM mcp_datasource WHERE datasource_ref LIKE 'acceptance-%'");
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        assertNotNull(value, name + " is required for real MySQL acceptance");
        assertFalse(value.isBlank(), name + " is required for real MySQL acceptance");
        return value;
    }

    private static String currentAcceptanceLogs() throws IOException {
        Path logDirectory = Path.of("data", "log");
        if (!Files.isDirectory(logDirectory)) return "";
        StringBuilder content = new StringBuilder();
        try (var paths = Files.list(logDirectory)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                content.append(Files.readString(path));
            }
        }
        return content.toString();
    }

    private static void assertDoesNotContain(String content, String... forbiddenValues) {
        String normalized = content == null ? "" : content.toLowerCase(Locale.ROOT);
        for (String forbidden : forbiddenValues) {
            if (forbidden == null || forbidden.isBlank()) continue;
            assertFalse(normalized.contains(forbidden.toLowerCase(Locale.ROOT)),
                    "Sensitive or internal value leaked");
        }
    }
}
