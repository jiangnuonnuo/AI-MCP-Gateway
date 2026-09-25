package cn.bugstack.ai.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/** Agent 调度测试中的单个真实 MCP/模型事件。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentTraceEventDTO implements Serializable {
    private long sequence;
    private String type;
    private String status;
    private String toolName;
    private String requestId;
    private String queryId;
    private long durationMs;
    private Map<String, Object> payload;
    private String errorCode;
    private String errorMessage;
}
