package cn.bugstack.ai.infrastructure.adapter.port;

import cn.bugstack.ai.domain.mysql.adapter.port.IMysqlQueryPort;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlQueryResult;
import cn.bugstack.ai.domain.session.adapter.repository.ISessionRepository;
import cn.bugstack.ai.domain.session.model.valobj.McpSchemaVO;
import cn.bugstack.ai.domain.session.service.message.handler.impl.ToolsCallHandler;
import cn.bugstack.ai.domain.session.service.message.handler.impl.ToolsListHandler;
import cn.bugstack.ai.domain.tool.executor.ToolExecutorRouter;
import cn.bugstack.ai.infrastructure.adapter.port.InMemoryToolAccessPolicy;
import cn.bugstack.ai.infrastructure.adapter.port.InMemoryMysqlTemplateRegistry;
import cn.bugstack.ai.infrastructure.config.MysqlMvpTemplateFactory;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MysqlMcpVerticalSliceTest {

    @Test
    void toolsListAndCallExposeStructuredMysqlResultWithoutSql() {
        InMemoryMysqlTemplateRegistry templates = new InMemoryMysqlTemplateRegistry();
        templates.register(MysqlMvpTemplateFactory.publishedTemplate());
        ISessionRepository sessionRepository = mock(ISessionRepository.class);
        when(sessionRepository.queryMcpGatewayToolConfigListByGatewayId("g")).thenReturn(List.of());
        when(sessionRepository.queryMcpGatewayProtocolConfig("g", MysqlMvpTemplateFactory.TEMPLATE_ID))
                .thenReturn(null);

        ToolsListHandler listHandler = new ToolsListHandler();
        ReflectionTestUtils.setField(listHandler, "repository", sessionRepository);
        ReflectionTestUtils.setField(listHandler, "mysqlTemplateRegistry", templates);
        McpSchemaVO.JSONRPCResponse listed = listHandler.handle("g",
                new McpSchemaVO.JSONRPCRequest("2.0", "tools/list", "list-1", Map.of()));
        Map<?, ?> listResult = (Map<?, ?>) listed.result();
        List<?> tools = (List<?>) listResult.get("tools");
        assertEquals(1, tools.size());
        assertFalse(tools.toString().contains("SELECT"));

        IMysqlQueryPort queryPort = command -> new MysqlQueryResult(command.getQueryId(),
                List.of(new MysqlQueryResult.MysqlColumn("channel_id", "channel_id", 4, "INT")),
                List.of(Map.of("channel_id", 1)), false, 1);
        MysqlTemplateExecutor mysqlExecutor = new MysqlTemplateExecutor(templates, queryPort);
        ToolsCallHandler callHandler = new ToolsCallHandler();
        ReflectionTestUtils.setField(callHandler, "repository", sessionRepository);
        ReflectionTestUtils.setField(callHandler, "mysqlTemplateRegistry", templates);
        ReflectionTestUtils.setField(callHandler, "toolExecutionPort",
                new ToolExecutorRouter(List.of(mysqlExecutor)));
        McpSchemaVO.JSONRPCResponse called = callHandler.handle("g", new McpSchemaVO.JSONRPCRequest(
                "2.0", "tools/call", "call-1", Map.of("name", MysqlMvpTemplateFactory.TEMPLATE_ID,
                "arguments", Map.of("fromTime", "2024-01-01 00:00:00", "toTime", "2025-01-01 00:00:00",
                        "orderStatus", "1"))));
        Map<?, ?> callResult = (Map<?, ?>) called.result();
        assertEquals(Boolean.FALSE, callResult.get("isError"));
        assertTrue(callResult.containsKey("columns"));
        assertInstanceOf(String.class, callResult.get("queryId"));
    }

    @Test
    void deniedToolDoesNotReachQueryPort() {
        InMemoryMysqlTemplateRegistry templates = new InMemoryMysqlTemplateRegistry();
        templates.register(MysqlMvpTemplateFactory.publishedTemplate());
        ISessionRepository sessionRepository = mock(ISessionRepository.class);
        when(sessionRepository.queryMcpGatewayProtocolConfig("g", MysqlMvpTemplateFactory.TEMPLATE_ID))
                .thenReturn(null);
        AtomicBoolean queried = new AtomicBoolean();
        IMysqlQueryPort queryPort = command -> {
            queried.set(true);
            throw new AssertionError("denied tool must not query");
        };
        InMemoryToolAccessPolicy accessPolicy = new InMemoryToolAccessPolicy();
        accessPolicy.deny("g", MysqlMvpTemplateFactory.TEMPLATE_ID);
        ToolsCallHandler callHandler = new ToolsCallHandler();
        ReflectionTestUtils.setField(callHandler, "repository", sessionRepository);
        ReflectionTestUtils.setField(callHandler, "mysqlTemplateRegistry", templates);
        ReflectionTestUtils.setField(callHandler, "accessPolicy", accessPolicy);
        ReflectionTestUtils.setField(callHandler, "toolExecutionPort",
                new ToolExecutorRouter(List.of(new MysqlTemplateExecutor(templates, queryPort))));
        var response = callHandler.handle("g", new McpSchemaVO.JSONRPCRequest("2.0", "tools/call", "r",
                Map.of("name", MysqlMvpTemplateFactory.TEMPLATE_ID, "arguments", Map.of())));
        assertEquals(Boolean.TRUE, ((Map<?, ?>) response.result()).get("isError"));
        assertFalse(queried.get());
    }
}
