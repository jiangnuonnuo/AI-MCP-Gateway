package cn.bugstack.ai.test.infrastructure.adapter.port;

import cn.bugstack.ai.domain.mysql.adapter.port.IMysqlQueryPort;
import cn.bugstack.ai.domain.mysql.adapter.port.IMysqlDataSourceRegistry;
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
import cn.bugstack.ai.infrastructure.adapter.port.InMemoryToolAccessPolicy;
import cn.bugstack.ai.infrastructure.adapter.port.InMemoryToolExecutionAudit;
import cn.bugstack.ai.infrastructure.adapter.port.MysqlTemplateExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MysqlMcpVerticalSliceTest {

    @Test
    void toolsListAndCallUseTheSameGatewayBoundProtocol() {
        ISessionRepository repository = mock(ISessionRepository.class);
        McpToolProtocolConfigVO protocol = protocol();
        when(repository.queryMcpGatewayToolConfigListByGatewayId("gateway-a"))
                .thenReturn(List.of(McpToolConfigVO.builder().gatewayId("gateway-a").toolId(1L)
                        .toolName("orderSummary").toolDescription("Order summary").toolVersion("1.0.0")
                        .status(1).mcpToolProtocolConfigVO(protocol).build()));
        when(repository.queryMcpGatewayProtocolConfig("gateway-a", "orderSummary")).thenReturn(protocol);

        FixedDataSourceRegistry dataSources = new FixedDataSourceRegistry();
        dataSources.save(MysqlDataSourceRef.builder().id("data-warehouse")
                .status(MysqlDataSourceStatus.ENABLED).policy(MysqlQueryPolicy.defaults()).build());
        MysqlTemplateQueryService queryService = new MysqlTemplateQueryService();
        ReflectionTestUtils.setField(queryService, "dataSourceRegistry", dataSources);
        ReflectionTestUtils.setField(queryService, "safetyPort",
                (cn.bugstack.ai.domain.mysql.adapter.port.ISqlSafetyPort)
                        (sql, parameters, policy) -> SqlSafetyDecision.allowed());
        ReflectionTestUtils.setField(queryService, "queryPort", (IMysqlQueryPort) command ->
                new MysqlQueryResult(command.getQueryId(), List.of(), List.of(Map.of("order_count", 2L)), false, 1));
        MysqlTemplateExecutor executor = new MysqlTemplateExecutor();
        ReflectionTestUtils.setField(executor, "queryService", queryService);
        ToolExecutorRouter router = new ToolExecutorRouter();
        ReflectionTestUtils.setField(router, "executors", List.of(executor));
        ReflectionTestUtils.setField(router, "auditPort", new InMemoryToolExecutionAudit());

        ToolsListHandler listHandler = new ToolsListHandler();
        ReflectionTestUtils.setField(listHandler, "repository", repository);
        ToolsCallHandler callHandler = new ToolsCallHandler();
        ReflectionTestUtils.setField(callHandler, "repository", repository);
        ReflectionTestUtils.setField(callHandler, "toolExecutionPort", router);
        ReflectionTestUtils.setField(callHandler, "accessPolicy", new InMemoryToolAccessPolicy());

        McpSchemaVO.JSONRPCResponse listed = listHandler.handle("gateway-a",
                new McpSchemaVO.JSONRPCRequest("2.0", "tools/list", "list", Map.of()));
        McpSchemaVO.JSONRPCResponse called = callHandler.handle("gateway-a",
                new McpSchemaVO.JSONRPCRequest("2.0", "tools/call", "call",
                        Map.of("name", "orderSummary", "arguments",
                                Map.of("fromTime", "2024-01-01", "toTime", "2025-01-01"))));

        assertTrue(listed.result().toString().contains("orderSummary"));
        assertFalse(listed.result().toString().contains("SELECT"));
        assertEquals(Boolean.FALSE, ((Map<?, ?>) called.result()).get("isError"));
    }

    private static McpToolProtocolConfigVO protocol() {
        return McpToolProtocolConfigVO.builder().protocolType("mysql").protocolId(900001L).status(1)
                .backendType(cn.bugstack.ai.domain.tool.model.valobj.ToolBackendType.MYSQL)
                .executionMode(cn.bugstack.ai.domain.tool.model.valobj.ToolExecutionMode.MYSQL_TEMPLATE)
                .requestProtocolMappings(List.of(
                        McpToolProtocolConfigVO.ProtocolMapping.builder().mappingType("request")
                                .fieldName("fromTime").mcpPath("fromTime").mcpType("string").isRequired(1).sortOrder(1).build(),
                        McpToolProtocolConfigVO.ProtocolMapping.builder().mappingType("request")
                                .fieldName("toTime").mcpPath("toTime").mcpType("string").isRequired(1).sortOrder(2).build()))
                .mysqlTemplateConfig(McpToolProtocolConfigVO.MysqlTemplateConfig.builder()
                        .protocolId(900001L).templateRef("900001").templateVersion("1.0.0")
                        .datasourceRef("data-warehouse").datasourceStatus(1)
                        .sql("SELECT COUNT(*) FROM fact_order WHERE order_time >= :fromTime AND order_time < :toTime")
                        .parameters(List.of(
                                cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplateParameter.builder()
                                        .name("fromTime").type(cn.bugstack.ai.domain.mysql.model.valobj.MysqlParameterType.STRING).required(true).build(),
                                cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplateParameter.builder()
                                        .name("toTime").type(cn.bugstack.ai.domain.mysql.model.valobj.MysqlParameterType.STRING).required(true).build()))
                        .policy(MysqlQueryPolicy.defaults()).build()).build();
    }

    private static final class FixedDataSourceRegistry implements IMysqlDataSourceRegistry {
        private MysqlDataSourceRef dataSource;

        @Override
        public java.util.Optional<MysqlDataSourceRef> find(String ref) {
            return java.util.Optional.ofNullable(dataSource);
        }

        @Override
        public void save(MysqlDataSourceRef dataSource) {
            this.dataSource = dataSource;
        }

        @Override
        public void delete(String ref) {
            this.dataSource = null;
        }
    }
}
