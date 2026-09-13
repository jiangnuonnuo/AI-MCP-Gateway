package cn.bugstack.ai.test.infrastructure.adapter.port;

import cn.bugstack.ai.domain.mysql.adapter.port.IMysqlQueryPort;
import cn.bugstack.ai.domain.mysql.adapter.port.IMysqlDataSourceRegistry;
import cn.bugstack.ai.domain.mysql.adapter.port.ISqlSafetyPort;
import cn.bugstack.ai.domain.session.model.valobj.gateway.McpToolProtocolConfigVO;
import cn.bugstack.ai.domain.tool.model.valobj.ToolExecutionContext;
import cn.bugstack.ai.domain.tool.model.valobj.ToolExecutionErrorCode;
import cn.bugstack.ai.types.exception.MysqlQueryException;
import cn.bugstack.ai.infrastructure.adapter.repository.InMemoryMysqlTemplateRegistry;
import cn.bugstack.ai.infrastructure.adapter.repository.InMemoryMysqlDataSourceRegistry;
import cn.bugstack.ai.infrastructure.adapter.port.MysqlTemplateExecutor;
import cn.bugstack.ai.config.MysqlMvpTemplateFactory;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlDataSourceRef;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlDataSourceStatus;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlQueryPolicy;
import cn.bugstack.ai.domain.mysql.service.MysqlTemplateQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MysqlTemplateExecutorTest {

    @Test
    void mapsBackendErrorsToStableToolErrors() {
        InMemoryMysqlTemplateRegistry registry = new InMemoryMysqlTemplateRegistry();
        registry.register(MysqlMvpTemplateFactory.publishedTemplate());
        InMemoryMysqlDataSourceRegistry dataSources = new InMemoryMysqlDataSourceRegistry();
        dataSources.save(MysqlDataSourceRef.builder().id(MysqlMvpTemplateFactory.DATASOURCE_REF)
                .status(MysqlDataSourceStatus.ENABLED).policy(MysqlQueryPolicy.defaults()).build());
        IMysqlQueryPort queryPort = command -> {
            throw new MysqlQueryException("QUERY_TIMEOUT", "internal timeout details");
        };
        MysqlTemplateQueryService queryService = new MysqlTemplateQueryService();
        ReflectionTestUtils.setField(queryService, "templateRegistry", registry);
        ReflectionTestUtils.setField(queryService, "dataSourceRegistry", dataSources);
        ReflectionTestUtils.setField(queryService, "safetyPort",
                (ISqlSafetyPort) (sql, params, policy) -> cn.bugstack.ai.domain.mysql.model.valobj.SqlSafetyDecision.allowed());
        ReflectionTestUtils.setField(queryService, "queryPort", queryPort);
        MysqlTemplateExecutor executor = new MysqlTemplateExecutor();
        ReflectionTestUtils.setField(executor, "queryService", queryService);
        ToolExecutionContext context = ToolExecutionContext.builder()
                .requestId("q-1").gatewayId("g").toolName(MysqlMvpTemplateFactory.TEMPLATE_ID)
                .backendConfiguration(new Object()).arguments(Map.of(
                        "fromTime", "2024-01-01 00:00:00",
                        "toTime", "2025-01-01 00:00:00",
                        "orderStatus", "1"))
                .protocolConfig(McpToolProtocolConfigVO.builder()
                        .mysqlTemplateConfig(McpToolProtocolConfigVO.MysqlTemplateConfig.builder()
                                .templateRef(MysqlMvpTemplateFactory.TEMPLATE_ID)
                                .templateVersion(MysqlMvpTemplateFactory.TEMPLATE_VERSION).build())
                        .build()).build();
        assertEquals(ToolExecutionErrorCode.QUERY_TIMEOUT, executor.execute(context).getErrorCode());
    }
}
