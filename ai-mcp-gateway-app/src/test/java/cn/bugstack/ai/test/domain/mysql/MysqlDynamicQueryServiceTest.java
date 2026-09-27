package cn.bugstack.ai.test.domain.mysql;

import cn.bugstack.ai.domain.mysql.adapter.port.IMysqlDataSourceRegistry;
import cn.bugstack.ai.domain.mysql.adapter.port.IMysqlQueryPort;
import cn.bugstack.ai.domain.mysql.adapter.port.ISqlSafetyPort;
import cn.bugstack.ai.domain.mysql.model.command.MysqlQueryCommand;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlDataSourceRef;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlDataSourceStatus;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlQueryPolicy;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlQueryResult;
import cn.bugstack.ai.domain.mysql.model.valobj.SqlSafetyDecision;
import cn.bugstack.ai.domain.mysql.service.MysqlTemplateQueryService;
import cn.bugstack.ai.types.exception.MysqlDomainException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MysqlDynamicQueryServiceTest {

    @Test
    void buildsUnifiedCommandWithServerBoundDatasourceAndDynamicSafetyMode() {
        CapturingQueryPort queryPort = new CapturingQueryPort();
        RecordingSafetyPort safetyPort = new RecordingSafetyPort(SqlSafetyDecision.allowed());
        MysqlTemplateQueryService service = service(queryPort, safetyPort);

        MysqlQueryResult result = service.executeDynamic(
                "SELECT order_id FROM fact_order WHERE order_id = :orderId",
                "warehouse", Map.of("orderId", 7), null, "dynamic-q-1");

        assertEquals("dynamic-q-1", result.getQueryId());
        assertTrue(safetyPort.dynamicCalled);
        assertFalse(safetyPort.templateCalled);
        assertEquals("warehouse", queryPort.command.getDatasourceRef());
        assertEquals("SELECT order_id FROM fact_order WHERE order_id = :orderId", queryPort.command.getSql());
        assertNull(queryPort.command.getTemplate());
        assertEquals(7, queryPort.command.getParameters().get("orderId"));
        assertEquals("dynamic-q-1", queryPort.command.getQueryId());
    }

    @Test
    void rejectsNonScalarOrUnboundDynamicParametersBeforeQueryPort() {
        CapturingQueryPort queryPort = new CapturingQueryPort();
        MysqlTemplateQueryService service = service(queryPort, new RecordingSafetyPort(SqlSafetyDecision.allowed()));

        MysqlDomainException error = assertThrows(MysqlDomainException.class, () -> service.executeDynamic(
                "SELECT order_id FROM fact_order WHERE order_id = :orderId",
                "warehouse", Map.of("orderId", List.of(7)), null, "dynamic-q-2"));

        assertEquals("SQL_PARAMETER_ERROR", error.getCode());
        assertNull(queryPort.command);
    }

    @Test
    void stopsBeforeQueryPortWhenDynamicPolicyRejects() {
        CapturingQueryPort queryPort = new CapturingQueryPort();
        MysqlTemplateQueryService service = service(queryPort,
                new RecordingSafetyPort(SqlSafetyDecision.rejected("read only policy rejected")));

        MysqlDomainException error = assertThrows(MysqlDomainException.class, () -> service.executeDynamic(
                "DELETE FROM fact_order WHERE order_id = :orderId",
                "warehouse", Map.of("orderId", 7), null, "dynamic-q-3"));

        assertEquals("SQL_POLICY_REJECTED", error.getCode());
        assertNull(queryPort.command);
    }

    private static MysqlTemplateQueryService service(IMysqlQueryPort queryPort, ISqlSafetyPort safetyPort) {
        MysqlTemplateQueryService service = new MysqlTemplateQueryService();
        ReflectionTestUtils.setField(service, "queryPort", queryPort);
        ReflectionTestUtils.setField(service, "safetyPort", safetyPort);
        ReflectionTestUtils.setField(service, "dataSourceRegistry", new FixedDataSourceRegistry());
        return service;
    }

    private static final class FixedDataSourceRegistry implements IMysqlDataSourceRegistry {
        private final MysqlDataSourceRef dataSource = MysqlDataSourceRef.builder().id("warehouse")
                .status(MysqlDataSourceStatus.ENABLED).policy(MysqlQueryPolicy.defaults()).build();

        @Override public Optional<MysqlDataSourceRef> find(String ref) { return Optional.of(dataSource); }
        @Override public void save(MysqlDataSourceRef dataSource) { }
        @Override public void delete(String ref) { }
    }

    private static final class CapturingQueryPort implements IMysqlQueryPort {
        private MysqlQueryCommand command;

        @Override
        public MysqlQueryResult execute(MysqlQueryCommand command) {
            this.command = command;
            return new MysqlQueryResult(command.getQueryId(), List.of(), List.of(), false, 0);
        }
    }

    private static final class RecordingSafetyPort implements ISqlSafetyPort {
        private final SqlSafetyDecision decision;
        private boolean dynamicCalled;
        private boolean templateCalled;

        private RecordingSafetyPort(SqlSafetyDecision decision) {
            this.decision = decision;
        }

        @Override
        public SqlSafetyDecision validate(String sql, Map<String, ?> parameters, MysqlQueryPolicy policy) {
            templateCalled = true;
            return decision;
        }

        @Override
        public SqlSafetyDecision validateDynamic(String sql, Map<String, ?> parameters, MysqlQueryPolicy policy) {
            dynamicCalled = true;
            return decision;
        }
    }
}
