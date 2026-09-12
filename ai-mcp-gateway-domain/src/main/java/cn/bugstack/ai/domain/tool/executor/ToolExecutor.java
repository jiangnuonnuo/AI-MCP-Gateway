package cn.bugstack.ai.domain.tool.executor;

import cn.bugstack.ai.domain.tool.model.valobj.ToolBackendType;
import cn.bugstack.ai.domain.tool.model.valobj.ToolExecutionContext;
import cn.bugstack.ai.domain.tool.model.valobj.ToolExecutionMode;
import cn.bugstack.ai.domain.tool.model.valobj.ToolExecutionResult;

/**
 * 单一后端执行策略。策略只负责自己的执行技术，路由和 MCP 响应转换由上层完成。
 */
public interface ToolExecutor {

    ToolBackendType backendType();

    ToolExecutionMode executionMode();

    default boolean supports(ToolExecutionContext context) {
        return context != null
                && backendType() == context.resolveBackendType()
                && executionMode() == context.resolveExecutionMode();
    }

    ToolExecutionResult execute(ToolExecutionContext context);
}
