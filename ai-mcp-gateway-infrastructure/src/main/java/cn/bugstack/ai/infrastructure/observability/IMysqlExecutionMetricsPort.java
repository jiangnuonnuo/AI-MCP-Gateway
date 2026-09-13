package cn.bugstack.ai.infrastructure.observability;

/** MySQL 技术执行指标端口，供 Infrastructure 内部适配器记录运行指标。 */
public interface IMysqlExecutionMetricsPort {

    /** 记录一次执行结果。 */
    void record(String outcome, long durationMs, int rowCount, long resultBytes);
}
