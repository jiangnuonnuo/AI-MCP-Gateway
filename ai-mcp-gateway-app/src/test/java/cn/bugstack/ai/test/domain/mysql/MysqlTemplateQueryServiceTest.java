package cn.bugstack.ai.test.domain.mysql;

import cn.bugstack.ai.domain.mysql.adapter.port.IMysqlDataSourceRegistry;
import cn.bugstack.ai.domain.mysql.adapter.port.IMysqlQueryPort;
import cn.bugstack.ai.domain.mysql.adapter.port.IMysqlProtocolRepository;
import cn.bugstack.ai.domain.mysql.adapter.port.ISqlSafetyPort;
import cn.bugstack.ai.domain.mysql.model.command.MysqlQueryCommand;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlDataSourceRef;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlDataSourceStatus;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlQueryPolicy;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlQueryResult;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplate;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplateParameter;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplateStatus;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplateTestReport;
import cn.bugstack.ai.domain.mysql.model.valobj.SqlSafetyDecision;
import cn.bugstack.ai.domain.mysql.service.MysqlTemplateQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MysqlTemplateQueryServiceTest {

    @Test
    void validatesAndExecutesPersistedProtocolWithoutRegistryFallback() {
        CapturingQueryPort queryPort = new CapturingQueryPort();
        MysqlTemplateQueryService service = service(queryPort, new FixedDataSourceRegistry(),
                (sql, parameters, policy) -> SqlSafetyDecision.allowed());
        MysqlTemplate template = MysqlTemplate.builder().id("10").version("1").name("orders")
                .description("orders").datasourceRef("warehouse").sql("SELECT * FROM fact_order WHERE id = :id")
                .parameters(List.of(MysqlTemplateParameter.builder().name("id")
                        .type(cn.bugstack.ai.domain.mysql.model.valobj.MysqlParameterType.INTEGER).required(true).build()))
                .status(MysqlTemplateStatus.ENABLED).policy(MysqlQueryPolicy.defaults()).build();

        service.execute(template, Map.of("id", 7), null, "q-1");

        assertEquals("10", queryPort.command.getTemplate().getId());
        assertEquals(7, queryPort.command.getParameters().get("id"));
    }

    @Test
    void disabledDatasourceFailsClosed() {
        MysqlTemplateQueryService service = service(new CapturingQueryPort(),
                new DisabledDataSourceRegistry(),
                (sql, parameters, policy) -> SqlSafetyDecision.allowed());
        MysqlTemplate template = MysqlTemplate.builder().id("10").version("1").name("orders")
                .datasourceRef("warehouse").sql("SELECT 1").parameters(List.of())
                .status(MysqlTemplateStatus.ENABLED).policy(MysqlQueryPolicy.defaults()).build();
        assertThrows(RuntimeException.class, () -> service.execute(template, Map.of(), null, "q-2"));
    }

    @Test
    void adminTestReturnsRealResultStagesAndMasksSensitiveParameters() {
        CapturingQueryPort queryPort = new CapturingQueryPort();
        MysqlTemplateQueryService service = service(queryPort, new FixedDataSourceRegistry(),
                (sql, parameters, policy) -> SqlSafetyDecision.allowed());
        MysqlTemplate template = MysqlTemplate.builder().id("10").version("1").name("orders")
                .datasourceRef("warehouse").sql("SELECT :id AS id, :token AS token")
                .parameters(List.of(
                        MysqlTemplateParameter.builder().name("id")
                                .type(cn.bugstack.ai.domain.mysql.model.valobj.MysqlParameterType.INTEGER).required(true).build(),
                        MysqlTemplateParameter.builder().name("token")
                                .type(cn.bugstack.ai.domain.mysql.model.valobj.MysqlParameterType.STRING).required(true).build()))
                .status(MysqlTemplateStatus.ENABLED).policy(MysqlQueryPolicy.defaults()).build();
        ReflectionTestUtils.setField(service, "protocolRepository", new IMysqlProtocolRepository() {
            @Override public Optional<MysqlTemplate> find(String ref, String version) { return Optional.of(template); }
            @Override public void save(MysqlTemplate protocol) { }
            @Override public void delete(String ref, String version) { }
        });

        MysqlTemplateTestReport report = service.executeWithReport("10", "1",
                Map.of("id", 7, "token", "secret-value"), "q-report");

        assertEquals(true, report.isSuccess());
        assertEquals("***", report.getRequestParameters().get("token"));
        assertEquals(5, report.getStages().size());
        assertEquals("q-report", report.getQueryId());
        assertEquals(7, queryPort.command.getParameters().get("id"));
        String safeReport = report.toStructuredMap().toString();
        org.junit.jupiter.api.Assertions.assertFalse(safeReport.contains("secret-value"));
        org.junit.jupiter.api.Assertions.assertFalse(safeReport.contains("jdbc:mysql://"));
    }

    @Test
    void adminTestStopsBeforeQueryPortWhenRequiredParameterIsMissing() {
        CapturingQueryPort queryPort = new CapturingQueryPort();
        MysqlTemplateQueryService service = service(queryPort, new FixedDataSourceRegistry(),
                (sql, parameters, policy) -> SqlSafetyDecision.allowed());
        MysqlTemplate template = MysqlTemplate.builder().id("10").version("1").name("orders")
                .datasourceRef("warehouse").sql("SELECT :id AS id")
                .parameters(List.of(MysqlTemplateParameter.builder().name("id")
                        .type(cn.bugstack.ai.domain.mysql.model.valobj.MysqlParameterType.INTEGER).required(true).build()))
                .status(MysqlTemplateStatus.ENABLED).policy(MysqlQueryPolicy.defaults()).build();
        ReflectionTestUtils.setField(service, "protocolRepository", new IMysqlProtocolRepository() {
            @Override public Optional<MysqlTemplate> find(String ref, String version) { return Optional.of(template); }
            @Override public void save(MysqlTemplate protocol) { }
            @Override public void delete(String ref, String version) { }
        });

        MysqlTemplateTestReport report = service.executeWithReport("10", "1", Map.of(), "q-missing");

        assertEquals(false, report.isSuccess());
        assertEquals("SQL_PARAMETER_ERROR", report.getErrorCode());
        assertEquals("PARAMETER_VALIDATION", report.getFailedStage());
        assertEquals(null, queryPort.command);
        assertEquals(cn.bugstack.ai.domain.mysql.model.valobj.MysqlExecutionStage.Status.PENDING,
                report.getStages().get(1).getStatus());
    }

    @Test
    void adminTestStopsBeforeQueryPortWhenPolicyRejects() {
        CapturingQueryPort queryPort = new CapturingQueryPort();
        MysqlTemplateQueryService service = service(queryPort, new FixedDataSourceRegistry(),
                (sql, parameters, policy) -> SqlSafetyDecision.rejected("read only policy rejected"));
        MysqlTemplate template = MysqlTemplate.builder().id("10").version("1").name("orders")
                .datasourceRef("warehouse").sql("DELETE FROM fact_order")
                .parameters(List.of()).status(MysqlTemplateStatus.ENABLED).policy(MysqlQueryPolicy.defaults()).build();
        ReflectionTestUtils.setField(service, "protocolRepository", new IMysqlProtocolRepository() {
            @Override public Optional<MysqlTemplate> find(String ref, String version) { return Optional.of(template); }
            @Override public void save(MysqlTemplate protocol) { }
            @Override public void delete(String ref, String version) { }
        });

        MysqlTemplateTestReport report = service.executeWithReport("10", "1", Map.of(), "q-policy");

        assertEquals(false, report.isSuccess());
        assertEquals("SQL_POLICY_REJECTED", report.getErrorCode());
        assertEquals("POLICY_VALIDATION", report.getFailedStage());
        assertEquals(null, queryPort.command);
    }

    private static MysqlTemplateQueryService service(IMysqlQueryPort queryPort,
                                                       IMysqlDataSourceRegistry dataSources,
                                                       ISqlSafetyPort safetyPort) {
        MysqlTemplateQueryService service = new MysqlTemplateQueryService();
        ReflectionTestUtils.setField(service, "queryPort", queryPort);
        ReflectionTestUtils.setField(service, "dataSourceRegistry", dataSources);
        ReflectionTestUtils.setField(service, "safetyPort", safetyPort);
        return service;
    }

    private static final class FixedDataSourceRegistry implements IMysqlDataSourceRegistry {
        private final MysqlDataSourceRef value = MysqlDataSourceRef.builder().id("warehouse")
                .status(MysqlDataSourceStatus.ENABLED).policy(MysqlQueryPolicy.defaults()).build();
        @Override public Optional<MysqlDataSourceRef> find(String ref) { return Optional.of(value); }
        @Override public void save(MysqlDataSourceRef dataSource) { }
        @Override public void delete(String ref) { }
    }

    private static final class DisabledDataSourceRegistry implements IMysqlDataSourceRegistry {
        @Override public Optional<MysqlDataSourceRef> find(String ref) {
            return Optional.of(MysqlDataSourceRef.builder().id(ref).status(MysqlDataSourceStatus.DISABLED)
                    .policy(MysqlQueryPolicy.defaults()).build());
        }
        @Override public void save(MysqlDataSourceRef dataSource) { }
        @Override public void delete(String ref) { }
    }

    private static final class CapturingQueryPort implements IMysqlQueryPort {
        private MysqlQueryCommand command;
        @Override public MysqlQueryResult execute(MysqlQueryCommand command) {
            this.command = command;
            return new MysqlQueryResult(command.getQueryId(), List.of(), List.of(), false, 0);
        }
    }
}
