package cn.bugstack.ai.domain.mysql.model.valobj;

/**
 * SQL 只读策略及资源上限。客户端可以请求更小的限制，但不能通过请求放宽这里的上限。
 */
public final class MysqlQueryPolicy {
    private final long maxSqlLength;
    private final int maxRows;
    private final long maxResultBytes;
    private final int maxColumns;
    private final long timeoutMs;
    private final boolean readOnly;

    public MysqlQueryPolicy(long maxSqlLength, int maxRows, long maxResultBytes,
                            int maxColumns, long timeoutMs, boolean readOnly) {
        if (maxSqlLength <= 0 || maxRows <= 0 || maxResultBytes <= 0 || maxColumns <= 0 || timeoutMs <= 0) {
            throw new IllegalArgumentException("query policy limits must be positive");
        }
        this.maxSqlLength = maxSqlLength;
        this.maxRows = maxRows;
        this.maxResultBytes = maxResultBytes;
        this.maxColumns = maxColumns;
        this.timeoutMs = timeoutMs;
        this.readOnly = readOnly;
    }

    public static MysqlQueryPolicy defaults() {
        return new MysqlQueryPolicy(64 * 1024, 1_000, 4 * 1024 * 1024, 128, 30_000, true);
    }

    public long getMaxSqlLength() { return maxSqlLength; }
    public int getMaxRows() { return maxRows; }
    public long getMaxResultBytes() { return maxResultBytes; }
    public int getMaxColumns() { return maxColumns; }
    public long getTimeoutMs() { return timeoutMs; }
    public boolean isReadOnly() { return readOnly; }

    public MysqlQueryPolicy boundedBy(MysqlQueryPolicy upperBound) {
        if (upperBound == null) return this;
        return new MysqlQueryPolicy(
                Math.min(maxSqlLength, upperBound.maxSqlLength),
                Math.min(maxRows, upperBound.maxRows),
                Math.min(maxResultBytes, upperBound.maxResultBytes),
                Math.min(maxColumns, upperBound.maxColumns),
                Math.min(timeoutMs, upperBound.timeoutMs),
                readOnly && upperBound.readOnly);
    }
}
