package cn.bugstack.ai.types.config;

/**
 * MySQL 目标库运行时技术上限。
 *
 * <p>该契约只承载连接池、并发、超时和结果规模等应用级约束，不包含业务数据源地址、
 * 账号、凭证或启停状态。</p>
 */
public interface MysqlRuntimeSettings {

    int getMaxPoolSize();

    long getConnectionTimeoutMs();

    long getValidationTimeoutMs();

    int getMaxConcurrentQueries();

    long getMaxSqlLength();

    int getMaxRows();

    long getMaxResultBytes();

    int getMaxColumns();

    long getMaxQueryTimeoutMs();
}
