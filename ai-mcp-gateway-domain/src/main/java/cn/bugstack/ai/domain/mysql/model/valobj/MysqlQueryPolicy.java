package cn.bugstack.ai.domain.mysql.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SQL 只读策略及资源上限。
 *
 * <p>客户端可以请求更小的限制，但不能通过请求放宽服务端上限。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MysqlQueryPolicy {

    /** SQL 最大长度。 */
    private long maxSqlLength;

    /** 最大返回行数。 */
    private int maxRows;

    /** 最大结果字节数。 */
    private long maxResultBytes;

    /** 最大返回列数。 */
    private int maxColumns;

    /** 查询超时时间，单位毫秒。 */
    private long timeoutMs;

    /** 是否允许只读执行。 */
    private boolean readOnly;

    public static MysqlQueryPolicy defaults() {
        return new MysqlQueryPolicy(64 * 1024, 1_000, 4 * 1024 * 1024, 128, 30_000, true);
    }

    /**
     * 校验策略边界，非法策略不得进入 SQL 责任链或 JDBC 执行器。
     */
    public void validate() {
        if (maxSqlLength <= 0 || maxRows <= 0 || maxResultBytes <= 0
                || maxColumns <= 0 || timeoutMs <= 0) {
            throw new IllegalArgumentException("query policy limits must be positive");
        }
    }

    public MysqlQueryPolicy boundedBy(MysqlQueryPolicy upperBound) {
        if (upperBound == null) {
            return this;
        }
        return new MysqlQueryPolicy(
                Math.min(maxSqlLength, upperBound.maxSqlLength),
                Math.min(maxRows, upperBound.maxRows),
                Math.min(maxResultBytes, upperBound.maxResultBytes),
                Math.min(maxColumns, upperBound.maxColumns),
                Math.min(timeoutMs, upperBound.timeoutMs),
                readOnly && upperBound.readOnly);
    }
}
