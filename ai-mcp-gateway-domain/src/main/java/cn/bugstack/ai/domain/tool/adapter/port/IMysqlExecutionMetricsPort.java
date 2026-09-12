package cn.bugstack.ai.domain.tool.adapter.port;

/** MySQL 查询指标端口，避免 Domain 依赖具体指标实现。 */
public interface IMysqlExecutionMetricsPort {
    void record(String outcome, long durationMs, int rowCount, long resultBytes);
}
