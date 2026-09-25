package cn.bugstack.ai.test.domain.session.message;

import cn.bugstack.ai.domain.session.model.valobj.McpSchemaVO;
import cn.bugstack.ai.domain.session.service.message.handler.impl.ToolsCallHandler;
import cn.bugstack.ai.domain.tool.model.valobj.ToolExecutionResult;
import cn.bugstack.ai.domain.tool.service.ToolInvocationService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ToolsCallHandlerSharedServiceTest {

    @Test
    void mcpToolsCallUsesTheSameDomainInvocationServiceAsManualTest() {
        ToolInvocationService invocation = mock(ToolInvocationService.class);
        when(invocation.execute(eq("gateway-a"), eq("orders"), any(), eq("request-1")))
                .thenReturn(ToolExecutionResult.structured("request-1", "query-1",
                        java.util.List.of(Map.of("name", "order_count")), java.util.List.of(java.util.List.of(2)), false));
        ToolsCallHandler handler = new ToolsCallHandler();
        ReflectionTestUtils.setField(handler, "toolInvocationService", invocation);

        McpSchemaVO.JSONRPCResponse response = handler.handle("gateway-a",
                new McpSchemaVO.JSONRPCRequest("2.0", "tools/call", "request-1",
                        Map.of("name", "orders", "arguments", Map.of("fromTime", "2026-09-01"))));

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) response.result();
        assertEquals("query-1", result.get("queryId"));
        verify(invocation).execute("gateway-a", "orders", Map.of("fromTime", "2026-09-01"), "request-1");
    }
}
