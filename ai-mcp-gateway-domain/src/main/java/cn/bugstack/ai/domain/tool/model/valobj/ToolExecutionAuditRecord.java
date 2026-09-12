package cn.bugstack.ai.domain.tool.model.valobj;

/**
 * 不含敏感值的 Tool 调用审计摘要。
 */
public record ToolExecutionAuditRecord(
        String gatewayId,
        String toolName,
        ToolBackendType backendType,
        String requestId,
        String policyDecision,
        long durationMs,
        int rowCount,
        long resultBytes,
        String errorCode) {
}
