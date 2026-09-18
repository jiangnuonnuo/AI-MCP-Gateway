package cn.bugstack.ai.config;

import cn.bugstack.ai.domain.mysql.model.valobj.MysqlQueryPolicy;
import cn.bugstack.ai.types.config.MysqlRuntimeSettings;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MySQL 运行时技术参数。
 *
 * <p>业务数据源的 JDBC 地址、账号、密文和状态属于控制库中的
 * {@code mcp_datasource} 记录，不在该配置对象中维护。此类只保留连接池、超时、并发
 * 和查询资源上限等应用运行参数；Infrastructure 通过统一的技术契约读取合并后的运行时
 * 数据源设置，避免反向依赖启动层。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ConfigurationProperties(prefix = "gateway.mysql")
public class MysqlConnectionProperties implements MysqlRuntimeSettings {

    /** 连接池最大连接数，由 App Config 统一约束。 */
    @Builder.Default
    private int maxPoolSize = 8;

    /** 获取连接超时时间，单位毫秒。 */
    @Builder.Default
    private long connectionTimeoutMs = 3_000;

    /** 连接校验超时时间，单位毫秒。 */
    @Builder.Default
    private long validationTimeoutMs = 1_000;

    /** 单个数据源的最大并发查询数。 */
    @Builder.Default
    private int maxConcurrentQueries = 8;

    /** SQL 最大长度。 */
    @Builder.Default
    private long maxSqlLength = 64 * 1024;

    /** 单次查询最大返回行数。 */
    @Builder.Default
    private int maxRows = 1_000;

    /** 单次查询最大结果字节数。 */
    @Builder.Default
    private long maxResultBytes = 4 * 1024 * 1024;

    /** 单次查询最大返回列数。 */
    @Builder.Default
    private int maxColumns = 128;

    /** 单次查询最大执行时间，单位毫秒。 */
    @Builder.Default
    private long maxQueryTimeoutMs = 30_000;

    /**
     * 将应用级技术上限转换为不可放宽的领域策略。
     *
     * <p>业务数据源和协议策略从控制库读取后，只能通过该策略进一步收紧执行范围。</p>
     */
    public MysqlQueryPolicy technicalPolicy() {
        validate();
        return new MysqlQueryPolicy(maxSqlLength, maxRows, maxResultBytes, maxColumns,
                maxQueryTimeoutMs, true);
    }

    /** 校验运行时参数，避免非法池配置进入 Infrastructure。 */
    public void validate() {
        if (maxPoolSize <= 0 || connectionTimeoutMs <= 0 || validationTimeoutMs <= 0
                || maxConcurrentQueries <= 0 || maxSqlLength <= 0 || maxRows <= 0
                || maxResultBytes <= 0 || maxColumns <= 0 || maxQueryTimeoutMs <= 0) {
            throw new IllegalArgumentException("mysql runtime limits must be positive");
        }
    }

}
