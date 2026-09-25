package cn.bugstack.ai.test.domain.llm;

import cn.bugstack.ai.domain.llm.model.valobj.AgentExecutionTrace;
import cn.bugstack.ai.domain.llm.model.valobj.AgentTraceContext;
import cn.bugstack.ai.domain.llm.service.impl.LLMService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LLMServiceTraceTest {

    @AfterEach
    void clearTrace() {
        AgentTraceContext.clear();
    }

    @Test
    void observesActualToolCallbackRequestAndResponse() throws Exception {
        ToolCallback delegate = mock(ToolCallback.class);
        when(delegate.getToolDefinition()).thenReturn(ToolDefinition.builder().name("orders")
                .description("read orders").inputSchema("{\"type\":\"object\"}").build());
        when(delegate.call("{\"fromTime\":\"2026-09-01\"}")).thenReturn("{\"queryId\":\"query-1\"}");
        LLMService service = new LLMService();
        Method wrap = LLMService.class.getDeclaredMethod("wrapToolCallbacks", ToolCallback[].class);
        wrap.setAccessible(true);
        ToolCallback wrapped = ((ToolCallback[]) wrap.invoke(service, (Object) new ToolCallback[]{delegate}))[0];
        AgentExecutionTrace trace = new AgentExecutionTrace("agent-1", "gateway-a", "request-1");
        AgentTraceContext.set(trace);

        assertEquals("{\"queryId\":\"query-1\"}", wrapped.call("{\"fromTime\":\"2026-09-01\"}"));

        var events = trace.snapshotEvents();
        assertEquals("TOOL_CALL_REQUEST", events.get(1).getType());
        assertEquals("TOOL_CALL_RESPONSE", events.get(2).getType());
        assertEquals("orders", events.get(1).getToolName());
        assertEquals("query-1", events.get(2).getQueryId());
        assertTrue(events.get(1).getPayload().toString().contains("fromTime"));
    }

    @Test
    void keepsTraceWhenCallbackRunsAfterRequestThreadContextIsCleared() throws Exception {
        ToolCallback delegate = mock(ToolCallback.class);
        when(delegate.getToolDefinition()).thenReturn(ToolDefinition.builder().name("orders")
                .description("read orders").inputSchema("{\"type\":\"object\"}").build());
        when(delegate.call("{}" )).thenReturn("{\"queryId\":\"query-async\"}");
        AgentExecutionTrace trace = new AgentExecutionTrace("agent-2", "gateway-a", "request-2");
        AgentTraceContext.set(trace);
        LLMService service = new LLMService();
        Method wrap = LLMService.class.getDeclaredMethod("wrapToolCallbacks", ToolCallback[].class);
        wrap.setAccessible(true);
        ToolCallback wrapped = ((ToolCallback[]) wrap.invoke(service, (Object) new ToolCallback[]{delegate}))[0];
        AgentTraceContext.clear();

        assertEquals("{\"queryId\":\"query-async\"}", wrapped.call("{}"));
        assertEquals("TOOL_CALL_RESPONSE", trace.snapshotEvents().get(2).getType());
        assertEquals("query-async", trace.snapshotEvents().get(2).getQueryId());
    }
}
