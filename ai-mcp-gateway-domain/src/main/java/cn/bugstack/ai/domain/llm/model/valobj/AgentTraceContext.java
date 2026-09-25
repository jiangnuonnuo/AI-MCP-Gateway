package cn.bugstack.ai.domain.llm.model.valobj;

/** 当前线程 Agent 测试 Trace 上下文；用于 MCP ToolCallback 记录真实调用。 */
public final class AgentTraceContext {

    private static final ThreadLocal<AgentExecutionTrace> CURRENT = new ThreadLocal<>();

    private AgentTraceContext() {
    }

    public static void set(AgentExecutionTrace trace) {
        CURRENT.set(trace);
    }

    public static AgentExecutionTrace current() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
