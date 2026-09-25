package cn.bugstack.ai.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/** Agent 调度测试报告；最终回答与真实 Tool Trace 分开呈现。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentTestDTO implements Serializable {
    private boolean success;
    private String agentTestId;
    private String gatewayId;
    private String requestId;
    private String status;
    private String message;
    private String finalAnswer;
    private List<AgentTraceEventDTO> events;
    private Map<String, Object> requestJson;
    private Map<String, Object> responseJson;
    private long durationMs;
    private String errorCode;
    private String errorMessage;
}
