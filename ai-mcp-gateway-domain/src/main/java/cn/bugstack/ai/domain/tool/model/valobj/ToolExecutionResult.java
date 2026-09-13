package cn.bugstack.ai.domain.tool.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolExecutionResult {

    /** 是否执行成功。 */
    private boolean success;

    /** 成功时的文本或结构化内容。 */
    private Object content;

    /** 稳定错误码，成功时为 NONE。 */
    private ToolExecutionErrorCode errorCode;

    /** 面向 MCP Client 的安全错误消息。 */
    private String errorMessage;

    /** 调用请求标识。 */
    private String requestId;

    /** MySQL 查询关联标识。 */
    private String queryId;

    /** 结构化结果列信息。 */
    private List<Map<String, Object>> columns;

    /** 结构化结果行数据。 */
    private List<List<Object>> rows;

    /** 返回行数。 */
    private int rowCount;

    /** 结果是否被资源上限截断。 */
    private boolean truncated;

    /** 结果估算字节数。 */
    private long resultBytes;

    public static ToolExecutionResult success(Object content) {
        return success(null, content);
    }

    public static ToolExecutionResult success(String requestId, Object content) {
        return create(true, content, ToolExecutionErrorCode.NONE, null,
                requestId, null, null, null, 0, false, 0L);
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
        return create(true, structured, ToolExecutionErrorCode.NONE, null,
                requestId, queryId, safeColumns, safeRows, safeRows.size(), truncated, resultBytes);
    }

    public static ToolExecutionResult failure(ToolExecutionErrorCode errorCode, String errorMessage) {
        return failure(null, errorCode, errorMessage);
    }

    public static ToolExecutionResult failure(String requestId,
                                              ToolExecutionErrorCode errorCode,
                                              String errorMessage) {
        return create(false, null, errorCode, errorMessage,
                requestId, null, null, null, 0, false, 0L);
    }

    private static ToolExecutionResult create(boolean success,
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
        ToolExecutionResult result = new ToolExecutionResult(success, content, errorCode, errorMessage,
                requestId, queryId, columns, rows, rowCount, truncated, resultBytes);
        result.normalize();
        return result;
    }

    /**
     * 统一结果中的错误码和集合边界。
     */
    public void normalize() {
        errorCode = errorCode == null ? ToolExecutionErrorCode.INTERNAL_ERROR : errorCode;
        columns = columns == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(columns));
        rows = rows == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(rows));
    }

    public boolean isError() {
        return !success;
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
