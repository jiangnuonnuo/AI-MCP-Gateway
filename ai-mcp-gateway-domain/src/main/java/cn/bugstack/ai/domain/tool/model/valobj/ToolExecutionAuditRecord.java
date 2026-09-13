package cn.bugstack.ai.domain.tool.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 不含敏感值的 Tool 调用审计摘要。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolExecutionAuditRecord {

    /** 发起调用的 Gateway 标识。 */
    private String gatewayId;

    /** Tool 名称。 */
    private String toolName;

    /** Tool 后端类型。 */
    private ToolBackendType backendType;

    /** 调用请求标识。 */
    private String requestId;

    /** 策略决策结果。 */
    private String policyDecision;

    /** 执行耗时，单位毫秒。 */
    private long durationMs;

    /** 返回行数。 */
    private int rowCount;

    /** 结果估算字节数。 */
    private long resultBytes;

    /** 稳定错误码，成功时为空。 */
    private String errorCode;
}
