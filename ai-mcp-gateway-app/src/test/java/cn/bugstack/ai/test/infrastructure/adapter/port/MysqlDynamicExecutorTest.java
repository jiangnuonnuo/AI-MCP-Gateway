package cn.bugstack.ai.test.infrastructure.adapter.port;

import cn.bugstack.ai.domain.mysql.adapter.port.IMysqlDataSourceRegistry;
import cn.bugstack.ai.domain.mysql.adapter.port.IMysqlQueryPort;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlDataSourceRef;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlDataSourceStatus;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlQueryPolicy;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlQueryResult;
import cn.bugstack.ai.domain.mysql.model.valobj.SqlSafetyDecision;
import cn.bugstack.ai.domain.mysql.service.MysqlTemplateQueryService;
import cn.bugstack.ai.domain.session.model.valobj.gateway.McpToolProtocolConfigVO;
import cn.bugstack.ai.domain.tool.model.valobj.ToolExecutionContext;
import cn.bugstack.ai.domain.tool.model.valobj.ToolExecutionErrorCode;
import cn.bugstack.ai.domain.tool.model.valobj.ToolExecutionMode;
import cn.bugstack.ai.infrastructure.adapter.port.MysqlDynamicExecutor;
import cn.bugstack.ai.infrastructure.mysql.MysqlTemplateParameterBinder;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MysqlDynamicExecutorTest {

    @Test
    void executesDynamicSqlAgainstOnlyTheConfiguredDatasource() {
        MysqlTemplateQueryService queryService = queryService(command -> new MysqlQueryResult(
                command.getQueryId(),
                List.of(new MysqlQueryResult.MysqlColumn("order_id", "order_id", "BIGINT")),
                List.of(Map.of("order_id", 7L)), false, 1));
        MysqlDynamicExecutor executor = new MysqlDynamicExecutor();
        ReflectionTestUtils.setField(executor, "queryService", queryService);

        var result = executor.execute(ToolExecutionContext.builder()
                .requestId("dynamic-q-1").gatewayId("dynamic-gateway").toolName("dynamicQuery")
                .protocolConfig(protocol()).arguments(Map.of(
                        "sql", "SELECT order_id FROM fact_order WHERE order_id = :orderId",
                        "parameters", Map.of("orderId", 7))).build());

        assertEquals(ToolExecutionErrorCode.NONE, result.getErrorCode());
        assertEquals("dynamic-q-1", result.getQueryId());
        assertEquals(1, result.getRowCount());
        assertFalse(result.isTruncated());
    }

    @Test
    void rejectsDatasourceAndPolicyOverridesInTheDynamicRequest() {
        MysqlDynamicExecutor executor = new MysqlDynamicExecutor();
        MysqlTemplateQueryService queryService = queryService(command -> {
            throw new AssertionError("query port must not be reached");
        });
        ReflectionTestUtils.setField(executor, "queryService", queryService);

        var result = executor.execute(ToolExecutionContext.builder()
                .requestId("dynamic-q-2").gatewayId("dynamic-gateway").toolName("dynamicQuery")
                .protocolConfig(protocol()).arguments(Map.of(
                        "sql", "SELECT 1",
                        "parameters", Map.of(),
                        "datasourceRef", "client-controlled")).build());

        assertEquals(ToolExecutionErrorCode.INVALID_ARGUMENT, result.getErrorCode());
    }

    @Test
    void bindsOnlyDynamicPlaceholdersOutsideLiteralsCommentsAndIdentifiers() {
        MysqlTemplateParameterBinder.BoundSql bound = new MysqlTemplateParameterBinder().bind(
                "SELECT ':orderId', `:orderId`, :orderId /* :ignored */", Map.of("orderId", 7));

        assertEquals("SELECT ':orderId', `:orderId`, ? /* :ignored */", bound.sql());
        assertEquals(List.of(7), bound.values());
    }

    private static McpToolProtocolConfigVO protocol() {
        return McpToolProtocolConfigVO.builder().protocolType("mysql").protocolId(990001L).status(1)
                .backendType(cn.bugstack.ai.domain.tool.model.valobj.ToolBackendType.MYSQL)
                .executionMode(ToolExecutionMode.MYSQL_DYNAMIC_READONLY)
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
