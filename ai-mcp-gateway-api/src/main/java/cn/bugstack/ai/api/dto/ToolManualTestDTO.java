package cn.bugstack.ai.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/** 管理端手动 Tool 测试报告，保留原始 MCP JSON 语义。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolManualTestDTO implements Serializable {
    private boolean success;
    private String testId;
    private String gatewayId;
    private String toolName;
    private String requestId;
    private String queryId;
    private Map<String, Object> requestArguments;
    private Map<String, Object> requestJson;
    private Map<String, Object> result;
    private Map<String, Object> responseJson;
    private List<ToolExecutionStageDTO> stages;
    private long durationMs;
    private String errorCode;
    private String errorMessage;
}
