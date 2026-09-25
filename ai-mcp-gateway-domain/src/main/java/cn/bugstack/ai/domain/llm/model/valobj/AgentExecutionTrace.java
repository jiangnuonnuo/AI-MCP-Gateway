package cn.bugstack.ai.domain.llm.model.valobj;

import cn.bugstack.ai.types.security.SensitiveDataSanitizer;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 单次 Agent 测试请求的内存 Trace。
 *
 * <p>Trace 只存在于当前请求生命周期，所有 payload 在进入事件前脱敏，避免把模型或 Tool 凭证写入报告。</p>
 */
@Getter
public class AgentExecutionTrace {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String agentTestId;
    private final String gatewayId;
    private final String requestId;
    private final AtomicLong sequence = new AtomicLong();
    private final List<AgentTraceEvent> events = new ArrayList<>();
    private final long startedAt = System.nanoTime();
    private volatile String status = "RUNNING";
    private volatile String finalAnswer;

    public AgentExecutionTrace(String agentTestId, String gatewayId, String requestId) {
        this.agentTestId = agentTestId;
        this.gatewayId = gatewayId;
        this.requestId = requestId;
        Map<String, Object> payload = new LinkedHashMap<>();
        if (gatewayId != null) payload.put("gatewayId", gatewayId);
        record("AGENT_STARTED", "RUNNING", null, null, null, payload);
    }

    public synchronized AgentTraceEvent record(String type, String eventStatus, String toolName,
                                                String queryId, String errorCode,
                                                Map<String, ?> payload) {
        AgentTraceEvent event = AgentTraceEvent.builder()
                .sequence(sequence.incrementAndGet())
                .type(type)
                .status(eventStatus)
                .toolName(toolName)
                .requestId(requestId)
                .queryId(queryId)
                .durationMs(0L)
                .payload(sanitizeMap(payload))
                .errorCode(errorCode)
                .build();
        events.add(event);
        return event;
    }

    public AgentTraceEvent toolRequest(String toolName, String input) {
        return record("TOOL_CALL_REQUEST", "RUNNING", toolName, null, null,
                payloadFromText("arguments", input));
    }

    public void toolSuccess(AgentTraceEvent requestEvent, String output, long startedAt) {
        if (requestEvent != null) requestEvent.setStatus("SUCCEEDED");
        if (requestEvent != null) requestEvent.setDurationMs(elapsedMs(startedAt));
        record("TOOL_CALL_RESPONSE", "SUCCEEDED", requestEvent == null ? null : requestEvent.getToolName(),
                findQueryId(output), null, payloadFromText("result", output));
    }

    public void toolFailure(AgentTraceEvent requestEvent, Throwable throwable, long startedAt) {
        String message = throwable == null || throwable.getMessage() == null
                ? "Tool call failed" : SensitiveDataSanitizer.sanitize(throwable.getMessage());
        if (requestEvent != null) {
            requestEvent.setStatus("FAILED");
            requestEvent.setDurationMs(elapsedMs(startedAt));
        }
        record("TOOL_CALL_ERROR", "FAILED", requestEvent == null ? null : requestEvent.getToolName(),
                null, "MCP_TOOL_CALL_FAILED", Map.of("message", message));
    }

    public void recordDiscoveredTools(List<String> toolNames) {
        record("TOOLS_LIST_RESPONSE", "SUCCEEDED", null, null, null,
                Map.of("tools", toolNames == null ? List.of() : toolNames));
    }

    /** 记录观察边界无法确认的必需事件，禁止把未发生的调用伪造成成功。 */
    public void recordUnobserved(String eventType, String toolName, String reason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (reason != null) payload.put("reason", SensitiveDataSanitizer.sanitize(reason));
        record(eventType, "UNOBSERVED", toolName, null, "TRACE_UNOBSERVED", payload);
    }

    public void finish(String answer, boolean success, String errorCode, String errorMessage) {
        this.finalAnswer = answer == null ? null : SensitiveDataSanitizer.sanitize(answer);
        this.status = success ? "SUCCEEDED" : "FAILED";
        Map<String, Object> payload = new LinkedHashMap<>();
        if (answer != null) payload.put("answer", this.finalAnswer);
        payload.put("durationMs", elapsedMs(startedAt));
        record("AGENT_FINISHED", this.status, null, null, errorCode, payload);
        AgentTraceEvent last = events.get(events.size() - 1);
        last.setErrorMessage(errorMessage == null ? null : SensitiveDataSanitizer.sanitize(errorMessage));
        last.setDurationMs(elapsedMs(startedAt));
    }

    public synchronized List<AgentTraceEvent> snapshotEvents() {
        return events.stream().map(event -> AgentTraceEvent.builder()
                .sequence(event.getSequence()).type(event.getType()).status(event.getStatus())
                .toolName(event.getToolName()).requestId(event.getRequestId()).queryId(event.getQueryId())
                .durationMs(event.getDurationMs()).payload(event.getPayload() == null ? Map.of() : new LinkedHashMap<>(event.getPayload()))
                .errorCode(event.getErrorCode()).errorMessage(event.getErrorMessage()).build()).toList();
    }

    public long durationMs() {
        return elapsedMs(startedAt);
    }

    private static long elapsedMs(long startedAt) {
        return Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
    }

    private static Map<String, Object> payloadFromText(String key, String text) {
        Map<String, Object> value = new LinkedHashMap<>();
        if (text == null) return value;
        try {
            value.put(key, OBJECT_MAPPER.readValue(text, Object.class));
        } catch (Exception ignored) {
            value.put(key, SensitiveDataSanitizer.sanitize(text));
        }
        return value;
    }

    private static String findQueryId(String output) {
        if (output == null || output.isBlank()) return null;
        try {
            Map<String, Object> result = OBJECT_MAPPER.readValue(output, new TypeReference<>() { });
            Object queryId = result.get("queryId");
            return queryId == null ? null : String.valueOf(queryId);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Map<String, Object> sanitizeMap(Map<String, ?> payload) {
        return SensitiveDataSanitizer.sanitizeMap(payload);
    }
}
