package cn.bugstack.ai.domain.llm.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/** Agent 调度测试中的单个真实事件。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentTraceEvent {
    /** 事件在当前测试中的顺序。 */
    private long sequence;
    /** 事件类型，例如 TOOLS_LIST_RESPONSE、TOOL_CALL_REQUEST。 */
    private String type;
    /** 事件状态，例如 RUNNING、SUCCEEDED、FAILED、UNOBSERVED。 */
    private String status;
    /** Tool 名称。 */
    private String toolName;
    /** 请求关联标识。 */
    private String requestId;
    /** 下游查询标识。 */
    private String queryId;
    /** 事件耗时。 */
    private long durationMs;
    /** 脱敏后的事件载荷。 */
    private Map<String, Object> payload;
    /** 稳定错误码。 */
    private String errorCode;
    /** 非敏感错误提示。 */
    private String errorMessage;
}
