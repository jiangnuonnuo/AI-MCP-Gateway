package cn.bugstack.ai.domain.tool.model.valobj;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 后端无关的 Tool 执行结果。
 *
 * <p>成功结果可以是兼容旧客户端的文本，也可以是结构化内容；失败结果只暴露稳定错误码和安全消息。</p>
 */
public final class ToolExecutionResult {

    private final boolean success;
    private final Object content;
    private final ToolExecutionErrorCode errorCode;
    private final String errorMessage;
    private final String requestId;
    private final String queryId;
    private final List<Map<String, Object>> columns;
    private final List<List<Object>> rows;
    private final int rowCount;
    private final boolean truncated;
    private final long resultBytes;

    private ToolExecutionResult(boolean success,
                                Object content,
                                ToolExecutionErrorCode errorCode,
                                String errorMessage,
                                String requestId,
                                String queryId,
                                List<Map<String, Object>> columns,
                                List<List<Object>> rows,
                                int rowCount,
                                boolean truncated,
                                long resultBytes) {
        this.success = success;
        this.content = content;
        this.errorCode = errorCode == null ? ToolExecutionErrorCode.INTERNAL_ERROR : errorCode;
        this.errorMessage = errorMessage;
        this.requestId = requestId;
        this.queryId = queryId;
        this.columns = columns == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(columns));
        this.rows = rows == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(rows));
        this.rowCount = rowCount;
        this.truncated = truncated;
        this.resultBytes = resultBytes;
    }

    public static ToolExecutionResult success(Object content) {
        return success(null, content);
    }

    public static ToolExecutionResult success(String requestId, Object content) {
        return new ToolExecutionResult(true, content, ToolExecutionErrorCode.NONE,
                null, requestId, null, null, null, 0, false, 0L);
    }

    public static ToolExecutionResult structured(String requestId,
                                                 String queryId,
                                                 List<Map<String, Object>> columns,
                                                 List<List<Object>> rows,
                                                 boolean truncated) {
        return structured(requestId, queryId, columns, rows, truncated, 0L);
    }

    public static ToolExecutionResult structured(String requestId,
                                                 String queryId,
                                                 List<Map<String, Object>> columns,
                                                 List<List<Object>> rows,
                                                 boolean truncated,
                                                 long resultBytes) {
        List<Map<String, Object>> safeColumns = columns == null ? List.of() : columns;
        List<List<Object>> safeRows = rows == null ? List.of() : rows;
        Map<String, Object> structured = new LinkedHashMap<>();
        structured.put("columns", safeColumns);
        structured.put("rows", safeRows);
        structured.put("rowCount", safeRows.size());
        structured.put("truncated", truncated);
        if (queryId != null) {
            structured.put("queryId", queryId);
        }
        return new ToolExecutionResult(true, structured, ToolExecutionErrorCode.NONE,
                null, requestId, queryId, safeColumns, safeRows, safeRows.size(), truncated, resultBytes);
    }

    public static ToolExecutionResult failure(ToolExecutionErrorCode errorCode, String errorMessage) {
        return failure(null, errorCode, errorMessage);
    }

    public static ToolExecutionResult failure(String requestId,
                                              ToolExecutionErrorCode errorCode,
                                              String errorMessage) {
        return new ToolExecutionResult(false, null, errorCode, errorMessage, requestId,
                null, null, null, 0, false, 0L);
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean isError() {
        return !success;
    }

    public Object getContent() {
        return content;
    }

    public ToolExecutionErrorCode getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getQueryId() {
        return queryId;
    }

    public List<Map<String, Object>> getColumns() {
        return columns;
    }

    public List<List<Object>> getRows() {
        return rows;
    }

    public int getRowCount() {
        return rowCount;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public long getResultBytes() {
        return resultBytes;
    }

    /**
     * 转换为 MCP tools/call 结果。isError 始终为 JSON 布尔值，而不是兼容旧实现的字符串。
     */
    public Map<String, Object> toMcpResult() {
        Map<String, Object> result = new LinkedHashMap<>();
        if (success) {
            if (content instanceof Map<?, ?> map) {
                map.forEach((key, value) -> result.put(String.valueOf(key), value));
                if (!result.containsKey("content")) {
                    result.put("content", List.of(Map.of("type", "text", "text", content.toString())));
                }
            } else {
                result.put("content", List.of(Map.of("type", "text", "text", content == null ? "" : content)));
            }
        } else {
            result.put("content", List.of(Map.of("type", "text", "text",
                    errorMessage == null ? errorCode.name() : errorMessage)));
            result.put("errorCode", errorCode.name());
        }
        result.put("isError", !success);
        if (requestId != null) {
            result.put("requestId", requestId);
        }
        if (queryId != null) {
            result.put("queryId", queryId);
        }
        return result;
    }
}
