package cn.bugstack.ai.test.domain.llm;

import cn.bugstack.ai.domain.llm.model.valobj.AgentExecutionTrace;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentExecutionTraceTest {

    @Test
    void acceptsMissingGatewayForValidationFailureTrace() {
        AgentExecutionTrace trace = new AgentExecutionTrace("agent-0", null, "request-0");

        trace.finish(null, false, "INVALID_ARGUMENT", "Gateway and message are required");

        assertEquals("FAILED", trace.getStatus());
        assertEquals("AGENT_STARTED", trace.snapshotEvents().get(0).getType());
    }

    @Test
    void recordsOnlyObservedCallsInOrder() {
        AgentExecutionTrace trace = new AgentExecutionTrace("agent-1", "gateway-a", "request-1");
        trace.recordDiscoveredTools(List.of("orders"));
        var request = trace.toolRequest("orders", "{\"fromTime\":\"2026-01-01\"}");
        trace.toolSuccess(request, "{\"queryId\":\"query-1\",\"rowCount\":1}", System.nanoTime());
        trace.finish("共 1 条", true, null, null);

        var events = trace.snapshotEvents();
        assertEquals(List.of("AGENT_STARTED", "TOOLS_LIST_RESPONSE", "TOOL_CALL_REQUEST",
                "TOOL_CALL_RESPONSE", "AGENT_FINISHED"), events.stream().map(e -> e.getType()).toList());
        assertEquals("query-1", events.get(3).getQueryId());
        assertEquals("SUCCEEDED", trace.getStatus());
    }

    @Test
    void masksNestedToolArgumentsAndDoesNotInventCalls() {
        AgentExecutionTrace trace = new AgentExecutionTrace("agent-1", "gateway-a", "request-1");
        trace.recordDiscoveredTools(List.of("orders"));
        trace.toolRequest("orders", "{\"apiKey\":\"secret-value\",\"nested\":{\"password\":\"abc\"}}");
        trace.finish("回答完成", true, null, null);

        assertFalse(trace.snapshotEvents().toString().contains("secret-value"));
        assertFalse(trace.snapshotEvents().toString().contains("abc"));
        assertEquals(1, trace.snapshotEvents().stream().filter(e -> "TOOL_CALL_REQUEST".equals(e.getType())).count());
        assertEquals(0, trace.snapshotEvents().stream().filter(e -> "TOOL_CALL_RESPONSE".equals(e.getType())).count());
    }

    @Test
    void zeroToolCallIsRepresentedExplicitly() {
        AgentExecutionTrace trace = new AgentExecutionTrace("agent-1", "gateway-a", "request-1");
        trace.recordDiscoveredTools(List.of("orders"));
        trace.finish("你好", true, null, null);

        assertTrue(trace.snapshotEvents().stream().noneMatch(e -> e.getType().startsWith("TOOL_CALL_")));
    }

    @Test
    void preservesUniqueSequenceForConcurrentObservedEventsAndUnobservedState() throws Exception {
        AgentExecutionTrace trace = new AgentExecutionTrace("agent-1", "gateway-a", "request-1");
        CountDownLatch start = new CountDownLatch(1);
        var threads = IntStream.range(0, 8).mapToObj(index -> new Thread(() -> {
            try { start.await(); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            trace.record("MODEL_EVENT", "SUCCEEDED", null, null, null, Map.of("index", index));
        })).toList();
        threads.forEach(Thread::start);
        start.countDown();
        for (Thread thread : threads) thread.join();
        trace.recordUnobserved("TOOL_CALL_RESPONSE", "orders", "callback observer unavailable");

        var events = trace.snapshotEvents();
        assertEquals(10, events.size());
        assertEquals(10, events.stream().map(event -> event.getSequence()).distinct().count());
        assertEquals("UNOBSERVED", events.get(events.size() - 1).getStatus());
    }
}
