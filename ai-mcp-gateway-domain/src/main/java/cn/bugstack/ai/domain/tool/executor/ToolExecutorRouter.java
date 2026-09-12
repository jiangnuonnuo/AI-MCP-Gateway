package cn.bugstack.ai.domain.tool.executor;

import cn.bugstack.ai.domain.tool.adapter.port.IToolExecutionPort;
import cn.bugstack.ai.domain.tool.adapter.port.IToolExecutionAuditPort;
import cn.bugstack.ai.domain.tool.model.valobj.ToolBackendType;
import cn.bugstack.ai.domain.tool.model.valobj.ToolExecutionContext;
import cn.bugstack.ai.domain.tool.model.valobj.ToolExecutionErrorCode;
import cn.bugstack.ai.domain.tool.model.valobj.ToolExecutionMode;
import cn.bugstack.ai.domain.tool.model.valobj.ToolExecutionResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 根据 Tool 配置路由到唯一后端执行器。
 */
@Component
public class ToolExecutorRouter implements IToolExecutionPort {

    private final List<ToolExecutor> executors;
    private final IToolExecutionAuditPort auditPort;

    public ToolExecutorRouter() {
        this(List.of(), record -> { });
    }

    public ToolExecutorRouter(List<ToolExecutor> executors) {
        this(executors, record -> { });
    }

    @Autowired
    public ToolExecutorRouter(List<ToolExecutor> executors, IToolExecutionAuditPort auditPort) {
        this.executors = executors == null ? List.of() : List.copyOf(executors);
        this.auditPort = auditPort == null ? record -> { } : auditPort;
    }

    @Override
    public ToolExecutionResult execute(ToolExecutionContext context) {
        long startedAt = System.nanoTime();
        if (context == null) {
            return finish(context, ToolExecutionResult.failure(ToolExecutionErrorCode.INVALID_ARGUMENT,
                    "Tool execution context is required"), startedAt);
        }

        ToolBackendType backendType = context.resolveBackendType();
        if (backendType == null || backendType == ToolBackendType.UNKNOWN) {
            return finish(context, ToolExecutionResult.failure(context.getRequestId(),
                    ToolExecutionErrorCode.UNKNOWN_BACKEND_TYPE,
                    "Unsupported tool backend type"), startedAt);
        }

        ToolExecutionMode executionMode = context.resolveExecutionMode();
        if (executionMode == null || executionMode == ToolExecutionMode.UNKNOWN) {
            return finish(context, ToolExecutionResult.failure(context.getRequestId(),
                    ToolExecutionErrorCode.UNKNOWN_EXECUTION_MODE,
                    "Unsupported tool execution mode"), startedAt);
        }

        for (ToolExecutor executor : executors) {
            if (executor != null && executor.supports(context)) {
                try {
                    ToolExecutionResult result = executor.execute(context);
                    return finish(context, result == null
                            ? ToolExecutionResult.failure(context.getRequestId(),
                            ToolExecutionErrorCode.BACKEND_ERROR,
                            "Tool executor returned no result")
                            : result, startedAt);
                } catch (RuntimeException ex) {
                    // 不将底层地址、凭证或完整堆栈透传到 MCP 响应。
                    return finish(context, ToolExecutionResult.failure(context.getRequestId(),
                            ToolExecutionErrorCode.BACKEND_ERROR,
                            "Tool backend execution failed"), startedAt);
                }
            }
        }

        ToolExecutionErrorCode errorCode = context.hasBackendConfiguration()
                ? ToolExecutionErrorCode.BACKEND_UNAVAILABLE
                : ToolExecutionErrorCode.MISSING_CONFIGURATION;
        return finish(context, ToolExecutionResult.failure(context.getRequestId(), errorCode,
                errorCode == ToolExecutionErrorCode.MISSING_CONFIGURATION
                        ? "Tool backend configuration is missing"
                        : "No executor is registered for the configured tool backend"), startedAt);
    }

    private ToolExecutionResult finish(ToolExecutionContext context, ToolExecutionResult result, long startedAt) {
        long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
        try {
            auditPort.record(new cn.bugstack.ai.domain.tool.model.valobj.ToolExecutionAuditRecord(
                    context == null ? null : context.getGatewayId(),
                    context == null ? null : context.getToolName(),
                    context == null ? ToolBackendType.UNKNOWN : context.resolveBackendType(),
                    context == null ? null : context.getRequestId(),
                    result != null && result.isSuccess() ? "ALLOWED" : "REJECTED",
                    durationMs,
                    result == null ? 0 : result.getRowCount(),
                    result == null ? 0 : result.getResultBytes(),
                    result == null || result.isSuccess() ? null : result.getErrorCode().name()));
        } catch (RuntimeException ignored) {
            // 审计适配器故障不得改变已确定的 Tool 结果。
        }
        return result;
    }
}
