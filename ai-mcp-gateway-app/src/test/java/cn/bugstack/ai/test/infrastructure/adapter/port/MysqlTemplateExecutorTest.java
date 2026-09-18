package cn.bugstack.ai.test.infrastructure.adapter.port;

import cn.bugstack.ai.domain.mysql.adapter.port.IMysqlDataSourceRegistry;
import cn.bugstack.ai.domain.mysql.adapter.port.IMysqlQueryPort;
import cn.bugstack.ai.domain.mysql.model.command.MysqlQueryCommand;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlDataSourceRef;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlDataSourceStatus;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlQueryPolicy;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlQueryResult;
import cn.bugstack.ai.domain.mysql.model.valobj.SqlSafetyDecision;
import cn.bugstack.ai.domain.mysql.service.MysqlTemplateQueryService;
import cn.bugstack.ai.domain.session.model.valobj.gateway.McpToolProtocolConfigVO;
import cn.bugstack.ai.domain.tool.model.valobj.ToolExecutionContext;
import cn.bugstack.ai.domain.tool.model.valobj.ToolExecutionErrorCode;
import cn.bugstack.ai.infrastructure.adapter.port.MysqlTemplateExecutor;
import cn.bugstack.ai.types.exception.MysqlQueryException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MysqlTemplateExecutorTest {

    @Test
    void executesServerResolvedPersistedProtocol() {
        MysqlTemplateExecutor executor = executor(command -> new MysqlQueryResult(command.getQueryId(),
                List.of(new MysqlQueryResult.MysqlColumn("order_count", "order_count", "BIGINT")),
                List.of(Map.of("order_count", 2L)), false, 1));

        var result = executor.execute(context());

        assertEquals(ToolExecutionErrorCode.NONE, result.getErrorCode());
        assertEquals(1, result.getRowCount());
    }

    @Test
    void mapsBackendErrorsWithoutLeakingDriverText() {
        MysqlTemplateExecutor executor = executor(command -> {
            throw new MysqlQueryException("QUERY_TIMEOUT", "jdbc:mysql://secret-host/internal timeout");
        });

        var result = executor.execute(context());

        assertEquals(ToolExecutionErrorCode.QUERY_TIMEOUT, result.getErrorCode());
        assertEquals("QUERY_TIMEOUT", result.getErrorMessage());
    }

    private static MysqlTemplateExecutor executor(IMysqlQueryPort queryPort) {
        MysqlTemplateQueryService service = new MysqlTemplateQueryService();
        ReflectionTestUtils.setField(service, "dataSourceRegistry", new FixedDataSourceRegistry());
        ReflectionTestUtils.setField(service, "safetyPort",
                (cn.bugstack.ai.domain.mysql.adapter.port.ISqlSafetyPort)
                        (sql, parameters, policy) -> SqlSafetyDecision.allowed());
        ReflectionTestUtils.setField(service, "queryPort", queryPort);
        MysqlTemplateExecutor executor = new MysqlTemplateExecutor();
        ReflectionTestUtils.setField(executor, "queryService", service);
        return executor;
    }

    private static ToolExecutionContext context() {
        return ToolExecutionContext.builder().requestId("q-1").gatewayId("gateway-a")
                .toolName("orderSummary")
                .arguments(Map.of("fromTime", "2024-01-01", "toTime", "2025-01-01"))
                .protocolConfig(McpToolProtocolConfigVO.builder().protocolType("mysql").protocolId(900001L).status(1)
                        .mysqlTemplateConfig(McpToolProtocolConfigVO.MysqlTemplateConfig.builder()
                                .protocolId(900001L).templateRef("900001").templateVersion("1.0.0")
                                .datasourceRef("data-warehouse")
                                .sql("SELECT COUNT(*) AS order_count FROM fact_order WHERE order_time >= :fromTime AND order_time < :toTime")
                                .parameters(List.of(
                                        cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplateParameter.builder()
                                                .name("fromTime").type(cn.bugstack.ai.domain.mysql.model.valobj.MysqlParameterType.STRING).required(true).build(),
                                        cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplateParameter.builder()
                                                .name("toTime").type(cn.bugstack.ai.domain.mysql.model.valobj.MysqlParameterType.STRING).required(true).build()))
                                .policy(MysqlQueryPolicy.defaults()).build()).build())
                .build();
    }

    private static final class FixedDataSourceRegistry implements IMysqlDataSourceRegistry {
        private final MysqlDataSourceRef dataSource = MysqlDataSourceRef.builder().id("data-warehouse")
                .status(MysqlDataSourceStatus.ENABLED).policy(MysqlQueryPolicy.defaults()).build();

        @Override public Optional<MysqlDataSourceRef> find(String ref) { return Optional.of(dataSource); }
        @Override public void save(MysqlDataSourceRef dataSource) { }
        @Override public void delete(String ref) { }
    }
}
