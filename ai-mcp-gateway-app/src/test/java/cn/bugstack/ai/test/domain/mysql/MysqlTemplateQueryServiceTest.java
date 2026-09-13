package cn.bugstack.ai.test.domain.mysql;

import cn.bugstack.ai.config.MysqlMvpTemplateFactory;
import cn.bugstack.ai.domain.mysql.adapter.port.ISqlSafetyPort;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlDataSourceRef;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlDataSourceStatus;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlQueryPolicy;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlQueryResult;
import cn.bugstack.ai.types.exception.MysqlDomainException;
import cn.bugstack.ai.domain.mysql.service.MysqlTemplateQueryService;
import cn.bugstack.ai.infrastructure.adapter.repository.InMemoryMysqlDataSourceRegistry;
import cn.bugstack.ai.infrastructure.adapter.repository.InMemoryMysqlTemplateRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 验证模板查询领域服务负责业务状态、参数和策略编排。 */
class MysqlTemplateQueryServiceTest {

    @Test
    void validatesBusinessStateBeforeCallingQueryPort() {
        InMemoryMysqlTemplateRegistry templates = new InMemoryMysqlTemplateRegistry();
        templates.save(MysqlMvpTemplateFactory.publishedTemplate());
        InMemoryMysqlDataSourceRegistry dataSources = dataSources(MysqlDataSourceStatus.ENABLED);
        AtomicReference<String> queryId = new AtomicReference<>();
        MysqlTemplateQueryService service = service(templates, dataSources, command -> {
            queryId.set(command.getQueryId());
            return new MysqlQueryResult(command.getQueryId(), java.util.List.of(), java.util.List.of(), false, 0);
        });

        service.execute(MysqlMvpTemplateFactory.TEMPLATE_ID, MysqlMvpTemplateFactory.TEMPLATE_VERSION,
                Map.of("fromTime", "2024-01-01", "toTime", "2025-01-01", "orderStatus", "1"),
                MysqlQueryPolicy.builder().maxRows(10).maxSqlLength(1024).maxResultBytes(1024)
                        .maxColumns(4).timeoutMs(1000).readOnly(true).build(), "q-1");

        assertEquals("q-1", queryId.get());
    }

    @Test
    void rejectsMissingParameterAndDisabledDataSourceBeforeQueryPort() {
        InMemoryMysqlTemplateRegistry templates = new InMemoryMysqlTemplateRegistry();
        templates.save(MysqlMvpTemplateFactory.publishedTemplate());
        AtomicReference<Boolean> called = new AtomicReference<>(false);
        MysqlTemplateQueryService service = service(templates, dataSources(MysqlDataSourceStatus.DISABLED), command -> {
            called.set(true);
            return null;
        });

        assertThrows(MysqlDomainException.class, () -> service.execute(
                MysqlMvpTemplateFactory.TEMPLATE_ID, MysqlMvpTemplateFactory.TEMPLATE_VERSION,
                Map.of("fromTime", "2024-01-01", "toTime", "2025-01-01"), null, "q-2"));
        assertEquals(Boolean.FALSE, called.get());
    }

    private static InMemoryMysqlDataSourceRegistry dataSources(MysqlDataSourceStatus status) {
        InMemoryMysqlDataSourceRegistry registry = new InMemoryMysqlDataSourceRegistry();
        registry.save(MysqlDataSourceRef.builder().id(MysqlMvpTemplateFactory.DATASOURCE_REF)
                .status(status).policy(MysqlQueryPolicy.defaults()).build());
        return registry;
    }

    private static MysqlTemplateQueryService service(InMemoryMysqlTemplateRegistry templates,
                                                     InMemoryMysqlDataSourceRegistry dataSources,
                                                     cn.bugstack.ai.domain.mysql.adapter.port.IMysqlQueryPort queryPort) {
        MysqlTemplateQueryService service = new MysqlTemplateQueryService();
        ReflectionTestUtils.setField(service, "templateRegistry", templates);
        ReflectionTestUtils.setField(service, "dataSourceRegistry", dataSources);
        ReflectionTestUtils.setField(service, "safetyPort",
                (ISqlSafetyPort) (sql, parameters, policy) ->
                        cn.bugstack.ai.domain.mysql.model.valobj.SqlSafetyDecision.allowed());
        ReflectionTestUtils.setField(service, "queryPort", queryPort);
        return service;
    }
}
