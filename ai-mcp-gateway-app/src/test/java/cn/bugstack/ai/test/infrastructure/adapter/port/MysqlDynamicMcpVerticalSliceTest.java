package cn.bugstack.ai.test.infrastructure.adapter.port;

import cn.bugstack.ai.domain.mysql.adapter.port.IMysqlDataSourceRegistry;
import cn.bugstack.ai.domain.mysql.adapter.port.IMysqlQueryPort;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlDataSourceRef;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlDataSourceStatus;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlQueryPolicy;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlQueryResult;
import cn.bugstack.ai.domain.mysql.model.valobj.SqlSafetyDecision;
import cn.bugstack.ai.domain.mysql.service.MysqlTemplateQueryService;
import cn.bugstack.ai.domain.session.adapter.repository.ISessionRepository;
import cn.bugstack.ai.domain.session.model.valobj.McpSchemaVO;
import cn.bugstack.ai.domain.session.model.valobj.gateway.McpToolConfigVO;
import cn.bugstack.ai.domain.session.model.valobj.gateway.McpToolProtocolConfigVO;
import cn.bugstack.ai.domain.session.service.message.handler.impl.ToolsCallHandler;
import cn.bugstack.ai.domain.session.service.message.handler.impl.ToolsListHandler;
import cn.bugstack.ai.domain.tool.executor.ToolExecutorRouter;
import cn.bugstack.ai.domain.tool.adapter.port.IToolExecutionPort;
import cn.bugstack.ai.domain.tool.model.valobj.ToolBackendType;
import cn.bugstack.ai.domain.tool.model.valobj.ToolExecutionErrorCode;
import cn.bugstack.ai.domain.tool.model.valobj.ToolExecutionMode;
import cn.bugstack.ai.domain.tool.service.ToolArgumentValidator;
import cn.bugstack.ai.domain.tool.service.ToolInvocationService;
import cn.bugstack.ai.infrastructure.adapter.port.InMemoryToolAccessPolicy;
import cn.bugstack.ai.infrastructure.adapter.port.InMemoryToolExecutionAudit;
import cn.bugstack.ai.infrastructure.adapter.port.MysqlDynamicExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MysqlDynamicMcpVerticalSliceTest {

    @Test
    void listsAndCallsDynamicToolWithTheSameGatewayBinding() throws Exception {
        ISessionRepository repository = mock(ISessionRepository.class);
        McpToolProtocolConfigVO protocol = protocol();
        when(repository.queryMcpGatewayToolConfigListByGatewayId("dynamic-gateway"))
                .thenReturn(List.of(McpToolConfigVO.builder().gatewayId("dynamic-gateway").toolId(1L)
                        .toolName("dynamicQuery").toolDescription("Dynamic read-only query").status(1)
                        .mcpToolProtocolConfigVO(protocol).build()));
        when(repository.queryMcpGatewayProtocolConfig("dynamic-gateway", "dynamicQuery"))
                .thenReturn(protocol);

        MysqlTemplateQueryService queryService = queryService(command -> new MysqlQueryResult(
                command.getQueryId(),
                List.of(new MysqlQueryResult.MysqlColumn("order_id", "order_id", "BIGINT")),
                List.of(Map.of("order_id", 7L)), false, 1));
        MysqlDynamicExecutor dynamicExecutor = new MysqlDynamicExecutor();
        ReflectionTestUtils.setField(dynamicExecutor, "queryService", queryService);

        ToolExecutorRouter router = new ToolExecutorRouter();
        ReflectionTestUtils.setField(router, "executors", List.of(dynamicExecutor));
        ReflectionTestUtils.setField(router, "auditPort", new InMemoryToolExecutionAudit());
        ToolInvocationService invocationService = new ToolInvocationService();
        ReflectionTestUtils.setField(invocationService, "repository", repository);
        ReflectionTestUtils.setField(invocationService, "toolExecutionPort", router);
        ReflectionTestUtils.setField(invocationService, "accessPolicy", new InMemoryToolAccessPolicy());
        ReflectionTestUtils.setField(invocationService, "argumentValidator", new ToolArgumentValidator());

        ToolsListHandler listHandler = new ToolsListHandler();
        ReflectionTestUtils.setField(listHandler, "repository", repository);
        ToolsCallHandler callHandler = new ToolsCallHandler();
        ReflectionTestUtils.setField(callHandler, "toolInvocationService", invocationService);

        McpSchemaVO.JSONRPCResponse listed = listHandler.handle("dynamic-gateway",
                new McpSchemaVO.JSONRPCRequest("2.0", "tools/list", "list-1", Map.of()));
        String listedJson = new ObjectMapper().writeValueAsString(listed.result());
        assertTrue(listedJson.contains("dynamicQuery"));
        assertTrue(listedJson.contains("\"sql\""));
        assertTrue(listedJson.contains("\"parameters\""));
        assertTrue(listedJson.contains("\"additionalProperties\":true"));
        assertFalse(listedJson.contains("datasourceRef"));
        assertFalse(listedJson.contains("jdbc"));
        assertFalse(listedJson.contains("policy"));

        McpSchemaVO.JSONRPCResponse called = callHandler.handle("dynamic-gateway",
                new McpSchemaVO.JSONRPCRequest("2.0", "tools/call", "call-1", Map.of(
                        "name", "dynamicQuery",
                        "arguments", Map.of("sql", "SELECT order_id FROM fact_order WHERE order_id = :orderId",
                                "parameters", Map.of("orderId", 7)))));
        Map<?, ?> callResult = (Map<?, ?>) called.result();
        assertEquals(Boolean.FALSE, callResult.get("isError"));
        assertEquals("call-1", callResult.get("queryId"));
        assertEquals(1, callResult.get("rowCount"));
    }

    @Test
    void rejectsUnknownDynamicFieldsBeforeTheQueryPort() {
        ISessionRepository repository = mock(ISessionRepository.class);
        McpToolProtocolConfigVO protocol = protocol();
        when(repository.queryMcpGatewayProtocolConfig("dynamic-gateway", "dynamicQuery")).thenReturn(protocol);
        ToolInvocationService invocationService = new ToolInvocationService();
        ReflectionTestUtils.setField(invocationService, "repository", repository);
        ReflectionTestUtils.setField(invocationService, "accessPolicy", new InMemoryToolAccessPolicy());
        ReflectionTestUtils.setField(invocationService, "argumentValidator", new ToolArgumentValidator());
        ReflectionTestUtils.setField(invocationService, "toolExecutionPort", (IToolExecutionPort) context -> {
            throw new AssertionError("query port must not be reached");
        });

        var result = invocationService.execute("dynamic-gateway", "dynamicQuery",
                Map.of("sql", "SELECT 1", "parameters", Map.of(), "datasourceRef", "client"), "call-2");

        assertEquals(ToolExecutionErrorCode.INVALID_ARGUMENT, result.getErrorCode());
    }

    private static McpToolProtocolConfigVO protocol() {
        return McpToolProtocolConfigVO.builder().protocolType("mysql").protocolId(990001L).status(1)
                .backendType(ToolBackendType.MYSQL).executionMode(ToolExecutionMode.MYSQL_DYNAMIC_READONLY)
                .mysqlTemplateConfig(McpToolProtocolConfigVO.MysqlTemplateConfig.builder()
                        .protocolId(990001L).datasourceRef("warehouse").datasourceStatus(1)
                        .policy(MysqlQueryPolicy.defaults()).build()).build();
    }

    private static MysqlTemplateQueryService queryService(IMysqlQueryPort queryPort) {
        MysqlTemplateQueryService service = new MysqlTemplateQueryService();
        ReflectionTestUtils.setField(service, "dataSourceRegistry", new FixedDataSourceRegistry());
        ReflectionTestUtils.setField(service, "safetyPort",
                (cn.bugstack.ai.domain.mysql.adapter.port.ISqlSafetyPort)
                        (sql, parameters, policy) -> SqlSafetyDecision.allowed());
        ReflectionTestUtils.setField(service, "queryPort", queryPort);
        return service;
    }

    private static final class FixedDataSourceRegistry implements IMysqlDataSourceRegistry {
        private final MysqlDataSourceRef source = MysqlDataSourceRef.builder().id("warehouse")
                .status(MysqlDataSourceStatus.ENABLED).policy(MysqlQueryPolicy.defaults()).build();

        @Override public Optional<MysqlDataSourceRef> find(String ref) { return Optional.of(source); }
        @Override public void save(MysqlDataSourceRef dataSource) { }
        @Override public void delete(String ref) { }
    }
}
