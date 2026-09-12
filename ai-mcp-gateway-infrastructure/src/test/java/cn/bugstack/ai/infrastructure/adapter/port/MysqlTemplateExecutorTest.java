package cn.bugstack.ai.infrastructure.adapter.port;

import cn.bugstack.ai.domain.mysql.adapter.port.IMysqlQueryPort;
import cn.bugstack.ai.domain.session.model.valobj.gateway.McpToolProtocolConfigVO;
import cn.bugstack.ai.domain.tool.model.valobj.ToolExecutionContext;
import cn.bugstack.ai.domain.tool.model.valobj.ToolExecutionErrorCode;
import cn.bugstack.ai.infrastructure.adapter.port.MysqlQueryException;
import cn.bugstack.ai.infrastructure.adapter.port.InMemoryMysqlTemplateRegistry;
import cn.bugstack.ai.infrastructure.config.MysqlMvpTemplateFactory;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MysqlTemplateExecutorTest {

    @Test
    void mapsBackendErrorsToStableToolErrors() {
        InMemoryMysqlTemplateRegistry registry = new InMemoryMysqlTemplateRegistry();
        registry.register(MysqlMvpTemplateFactory.publishedTemplate());
        IMysqlQueryPort queryPort = command -> {
            throw new MysqlQueryException("QUERY_TIMEOUT", "internal timeout details");
        };
        MysqlTemplateExecutor executor = new MysqlTemplateExecutor(registry, queryPort);
        ToolExecutionContext context = ToolExecutionContext.builder()
                .requestId("q-1").gatewayId("g").toolName(MysqlMvpTemplateFactory.TEMPLATE_ID)
                .backendConfiguration(new Object()).arguments(Map.of())
                .protocolConfig(McpToolProtocolConfigVO.builder()
                        .mysqlTemplateConfig(McpToolProtocolConfigVO.MysqlTemplateConfig.builder()
                                .templateRef(MysqlMvpTemplateFactory.TEMPLATE_ID)
                                .templateVersion(MysqlMvpTemplateFactory.TEMPLATE_VERSION).build())
                        .build()).build();
        assertEquals(ToolExecutionErrorCode.QUERY_TIMEOUT, executor.execute(context).getErrorCode());
    }
}
