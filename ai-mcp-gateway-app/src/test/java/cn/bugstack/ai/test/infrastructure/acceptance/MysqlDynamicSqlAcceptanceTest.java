package cn.bugstack.ai.test.infrastructure.acceptance;

import cn.bugstack.ai.domain.session.model.valobj.SessionConfigVO;
import cn.bugstack.ai.domain.session.model.valobj.enums.SessionTransportTypeEnumVO;
import cn.bugstack.ai.domain.session.service.ISessionManagementService;
import cn.bugstack.ai.domain.session.model.valobj.McpSchemaVO;
import cn.bugstack.ai.domain.session.service.message.handler.impl.ToolsCallHandler;
import cn.bugstack.ai.infrastructure.redis.IRedisService;
import cn.bugstack.ai.api.dto.GatewayConfigRequestDTO;
import cn.bugstack.ai.api.dto.MysqlDynamicBindingRequestDTO;
import cn.bugstack.ai.trigger.http.AdminController;
import cn.bugstack.ai.trigger.http.AdminMysqlController;
import cn.bugstack.ai.trigger.http.McpSSEGatewayController;
import cn.bugstack.ai.trigger.http.McpStreamableGatewayController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import reactor.core.Disposable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/** 真实控制库、真实数据源和真实 MCP 消息链路验收；必须显式打开环境开关。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.main.allow-bean-definition-overriding=true")
@Import(MysqlDynamicSqlAcceptanceTest.RedisTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "OPENSPEC_REAL_MYSQL_ACCEPTANCE", matches = "1")
class MysqlDynamicSqlAcceptanceTest {

    private static final String GATEWAY_ID = "openspec_dynamic_gateway";
    private static final String TOOL_NAME = "dynamicReadonlyQuery";
    private Long protocolId;
    private Long bindingId;
    @Autowired private JdbcTemplate controlJdbcTemplate;
    @Autowired private McpSSEGatewayController mcpSseGatewayController;
    @Autowired private McpStreamableGatewayController mcpStreamableGatewayController;
    @Autowired private ToolsCallHandler toolsCallHandler;
    @Autowired private ISessionManagementService sessionManagementService;
    @Autowired private AdminController adminController;
    @Autowired private AdminMysqlController adminMysqlController;

    @TestConfiguration(proxyBeanMethods = false)
    static class RedisTestConfiguration {
        @Bean RedissonClient redissonClient() { return Mockito.mock(RedissonClient.class); }

        @Bean IRedisService redisService() {
            IRedisService service = Mockito.mock(IRedisService.class);
            RMap<String, cn.bugstack.ai.domain.session.model.valobj.SessionSyncInfoVO> sessions = Mockito.mock(RMap.class);
            RTopic topic = Mockito.mock(RTopic.class);
            when(service.<String, cn.bugstack.ai.domain.session.model.valobj.SessionSyncInfoVO>getMap(anyString()))
                    .thenReturn(sessions);
            when(sessions.readAllValues()).thenReturn(List.of());
            when(service.getTopic(anyString())).thenReturn(topic);
            return service;
        }
    }

    @BeforeEach
    void installDedicatedGateway() {
        deleteFixture();
        assertEquals("0000", adminController.saveGatewayConfig(GatewayConfigRequestDTO.GatewayConfig.builder()
                .gatewayId(GATEWAY_ID).gatewayName("OpenSpec dynamic acceptance")
                .gatewayDesc("Dynamic SQL acceptance gateway").version("1.0.0").auth(0).status(1).build()).getCode());
        var bindingResponse = adminMysqlController.saveMysqlDynamicBinding(MysqlDynamicBindingRequestDTO.builder()
                .gatewayId(GATEWAY_ID).toolName(TOOL_NAME).toolDescription("Dynamic read-only SQL")
                .toolVersion("1.0.0").datasourceRef("data-warehouse").maxRows(10).maxResultBytes(1048576L)
                .maxColumns(8).timeoutMs(3000).status(1).build());
        assertEquals("0000", bindingResponse.getCode());
        protocolId = bindingResponse.getData().getProtocolId();
        bindingId = bindingResponse.getData().getId();
        assertEquals("0000", adminMysqlController.changeMysqlBindingStatus(bindingId, 1).getCode());

        Map<String, Object> binding = controlJdbcTemplate.queryForMap("""
                SELECT g.gateway_id, t.tool_name, t.status AS tool_status,
                       p.protocol_id, p.execution_mode, p.status AS protocol_status,
                       d.datasource_ref, d.status AS datasource_status
                FROM mcp_gateway g
                JOIN mcp_gateway_tool t ON t.gateway_id = g.gateway_id
                JOIN mcp_protocol_mysql p ON p.protocol_id = t.protocol_id
                JOIN mcp_datasource d ON d.id = p.datasource_id
                WHERE g.gateway_id = ? AND t.tool_name = ?
                """, GATEWAY_ID, TOOL_NAME);
        assertEquals(GATEWAY_ID, binding.get("gateway_id"));
        assertEquals(TOOL_NAME, binding.get("tool_name"));
        assertEquals(protocolId, ((Number) binding.get("protocol_id")).longValue());
        assertEquals("DYNAMIC_READONLY", binding.get("execution_mode"));
        assertEquals("data-warehouse", binding.get("datasource_ref"));
        assertEnabled(binding.get("tool_status"));
        assertEnabled(binding.get("protocol_status"));
        assertEnabled(binding.get("datasource_status"));
    }

    @AfterEach
    void removeDedicatedGateway() {
        deleteFixture();
    }

    @Test
    void executesDynamicToolThroughSseAndStreamableMessageChains() throws Exception {
        String listRequest = "{\"jsonrpc\":\"2.0\",\"method\":\"tools/list\",\"id\":\"list-sse\",\"params\":{}}";
        String callRequest = "{\"jsonrpc\":\"2.0\",\"method\":\"tools/call\",\"id\":\"call-sse\",\"params\":{\"name\":\""
                + TOOL_NAME + "\",\"arguments\":{\"sql\":\"SELECT COUNT(*) AS order_count FROM fact_order WHERE order_id <= :maxOrderId\",\"parameters\":{\"maxOrderId\":7}}}}";

        SessionConfigVO sse = sessionManagementService.createSession(GATEWAY_ID, "", SessionTransportTypeEnumVO.SSE);
        List<ServerSentEvent<String>> sseEvents = new ArrayList<>();
        Disposable subscription = sse.getSink().asFlux().subscribe(sseEvents::add);
        try {
            assertEquals(202, mcpSseGatewayController.handleMessage(
                    GATEWAY_ID, sse.getSessionId(), "", listRequest).block().getStatusCodeValue());
            String listJson = lastMessage(sseEvents);
            assertTrue(listJson.contains("dynamicReadonlyQuery"));
            assertTrue(listJson.contains("\"sql\""));
            assertTrue(listJson.contains("\"parameters\""));
            org.junit.jupiter.api.Assertions.assertFalse(listJson.contains("data-warehouse"));
            assertEquals(202, mcpSseGatewayController.handleMessage(
                    GATEWAY_ID, sse.getSessionId(), "", callRequest).block().getStatusCodeValue());
            String callJson = lastMessage(sseEvents);
            assertTrue(callJson.contains("\"queryId\":\"call-sse\""));
            assertTrue(callJson.contains("\"order_count\""));
            assertTrue(callJson.contains("7"));
        } finally {
            subscription.dispose();
            sessionManagementService.removeSession(sse.getSessionId());
        }

        SessionConfigVO streamable = sessionManagementService.createSession(
                GATEWAY_ID, "", SessionTransportTypeEnumVO.STREAMABLE);
        try {
            ResponseEntity<?> listResponse = mcpStreamableGatewayController.handlePost(
                    GATEWAY_ID, "", streamable.getSessionId(), null, listRequest, new HttpHeaders()).block();
            ResponseEntity<?> callResponse = mcpStreamableGatewayController.handlePost(
                    GATEWAY_ID, "", streamable.getSessionId(), null, callRequest, new HttpHeaders()).block();
            assertEquals(200, listResponse.getStatusCodeValue());
            assertEquals(200, callResponse.getStatusCodeValue());
            assertTrue(String.valueOf(callResponse.getBody()).contains("\"queryId\":\"call-sse\""));
            assertTrue(String.valueOf(callResponse.getBody()).contains("\"order_count\""));
            assertTrue(String.valueOf(callResponse.getBody()).contains("7"));
            org.junit.jupiter.api.Assertions.assertFalse(String.valueOf(callResponse.getBody()).contains("data-warehouse"));
        } finally {
            sessionManagementService.removeSession(streamable.getSessionId());
        }

        assertEquals(1L, controlJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mcp_gateway_tool WHERE gateway_id = ? AND tool_name = ? AND status = 1",
                Long.class, GATEWAY_ID, TOOL_NAME));
        assertEquals("DYNAMIC_READONLY", controlJdbcTemplate.queryForObject(
                "SELECT execution_mode FROM mcp_protocol_mysql WHERE protocol_id = ?", String.class, protocolId));
    }

    @Test
    void rejectsDynamicSqlMatrixOnTheDedicatedGateway() {
        Map<String, String> rejectedSql = Map.of(
                "write", "DELETE FROM fact_order",
                "ddl", "DROP TABLE fact_order",
                "lock", "SELECT 1 FOR UPDATE",
                "file", "SELECT 1 INTO OUTFILE '/tmp/openspec-result'",
                "multi", "SELECT 1; SELECT 2",
                "timeout", "SELECT SLEEP(2)",
                "syntax", "SELECT FROM");
        for (String sql : rejectedSql.values()) {
            Map<?, ?> result = callTool(Map.of("sql", sql, "parameters", Map.of()));
            assertEquals(Boolean.TRUE, result.get("isError"), result::toString);
            assertTrue(Set.of("SQL_POLICY_REJECTED", "SQL_SYNTAX_ERROR").contains(result.get("errorCode")),
                    result::toString);
        }

        assertEquals("SQL_PARAMETER_ERROR", callTool(Map.of(
                "sql", "SELECT :answer AS answer", "parameters", Map.of())).get("errorCode"));
        assertEquals("SQL_PARAMETER_ERROR", callTool(Map.of(
                "sql", "SELECT 1", "parameters", Map.of("unused", 7))).get("errorCode"));
        assertEquals("INVALID_ARGUMENT", callTool(Map.of(
                "sql", "SELECT 1", "parameters", Map.of(), "datasourceRef", "client")).get("errorCode"));

        Map<?, ?> boundedResult = callTool(Map.of(
                "sql", "SELECT order_id FROM fact_order", "parameters", Map.of()));
        assertEquals(Boolean.FALSE, boundedResult.get("isError"), boundedResult::toString);
        assertEquals(Boolean.TRUE, boundedResult.get("truncated"), boundedResult::toString);
        assertEquals(10, boundedResult.get("rowCount"), boundedResult::toString);

        controlJdbcTemplate.update("UPDATE mcp_gateway_tool SET status = 0 WHERE gateway_id = ? AND tool_name = ?",
                GATEWAY_ID, TOOL_NAME);
        assertEquals("TOOL_DISABLED", callTool(Map.of("sql", "SELECT 1", "parameters", Map.of())).get("errorCode"));
        controlJdbcTemplate.update("UPDATE mcp_gateway_tool SET status = 1 WHERE gateway_id = ? AND tool_name = ?",
                GATEWAY_ID, TOOL_NAME);
        controlJdbcTemplate.update("UPDATE mcp_datasource SET status = 0 WHERE datasource_ref = 'data-warehouse'");
        assertEquals("DATASOURCE_UNAVAILABLE", callTool(Map.of("sql", "SELECT 1", "parameters", Map.of())).get("errorCode"));
        controlJdbcTemplate.update("UPDATE mcp_datasource SET status = 1 WHERE datasource_ref = 'data-warehouse'");
    }

    private Map<?, ?> callTool(Map<String, ?> arguments) {
        return (Map<?, ?>) toolsCallHandler.handle(GATEWAY_ID,
                new McpSchemaVO.JSONRPCRequest("2.0", "tools/call", "dynamic-call", Map.of(
                        "name", TOOL_NAME, "arguments", arguments))).result();
    }

    private static String lastMessage(List<ServerSentEvent<String>> events) {
        return events.stream().filter(event -> "message".equals(event.event()))
                .map(ServerSentEvent::data).reduce((first, second) -> second).orElseThrow();
    }

    private static void assertEnabled(Object status) {
        if (status instanceof Boolean value) {
            assertTrue(value);
        } else {
            assertEquals(1, ((Number) status).intValue());
        }
    }

    private void deleteFixture() {
        controlJdbcTemplate.update("DELETE FROM mcp_gateway_tool WHERE gateway_id = ?", GATEWAY_ID);
        if (protocolId != null) controlJdbcTemplate.update("DELETE FROM mcp_protocol_mysql WHERE protocol_id = ?", protocolId);
        controlJdbcTemplate.update("DELETE FROM mcp_gateway WHERE gateway_id = ?", GATEWAY_ID);
    }
}
