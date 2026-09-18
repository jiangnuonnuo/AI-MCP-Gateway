package cn.bugstack.ai.test.domain.mysql;

import cn.bugstack.ai.domain.mysql.adapter.port.IMysqlDataSourceRegistry;
import cn.bugstack.ai.domain.mysql.adapter.port.IMysqlQueryPort;
import cn.bugstack.ai.domain.mysql.adapter.port.ISqlSafetyPort;
import cn.bugstack.ai.domain.mysql.model.command.MysqlQueryCommand;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlDataSourceRef;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlDataSourceStatus;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlQueryPolicy;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlQueryResult;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplate;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplateParameter;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplateStatus;
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
