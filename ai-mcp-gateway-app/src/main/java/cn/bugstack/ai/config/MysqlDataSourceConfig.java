package cn.bugstack.ai.config;

import cn.bugstack.ai.types.config.MysqlConnectionSettings;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单个 MySQL 数据源的启动配置。
 *
 * <p>该对象只承载连接池和执行上限等应用启动参数，不承载数据源生命周期或 SQL 业务规则。</p>
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class MysqlDataSourceConfig implements MysqlConnectionSettings {

    /** 数据源唯一标识；通常由配置 Map 的键补齐。 */
    private String id;

    /** JDBC 连接地址。 */
    private String jdbcUrl;

    /** JDBC 登录用户名。 */
    private String username;

    /** 是否允许该数据源参与业务查询；停用仍保留配置但由 Domain 拒绝执行。 */
    @Builder.Default
    private boolean enabled = true;

    /** 密钥引用，例如 env:MYSQL_PASSWORD。 */
    private String passwordSecretRef;

    /** 测试或本地运行时直接提供的密码，不建议用于生产配置。 */
    private String runtimePassword;

    /** 连接池最大连接数。 */
    @Builder.Default
    private int maxPoolSize = 8;

    /** 获取连接超时时间，单位毫秒。 */
    @Builder.Default
    private long connectionTimeoutMs = 3_000;

    /** 连接校验超时时间，单位毫秒。 */
    @Builder.Default
    private long validationTimeoutMs = 1_000;

    /** 最大并发查询数。 */
    @Builder.Default
    private int maxConcurrentQueries = 8;

    /** 单条 SQL 最大长度。 */
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

    /** 校验启动参数，避免非法连接池配置进入技术适配器。 */
    public void validate() {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new IllegalArgumentException("jdbcUrl must not be blank");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
        if (maxPoolSize <= 0 || connectionTimeoutMs <= 0 || validationTimeoutMs <= 0
                || maxConcurrentQueries <= 0 || maxSqlLength <= 0 || maxRows <= 0
                || maxResultBytes <= 0 || maxColumns <= 0 || maxQueryTimeoutMs <= 0) {
            throw new IllegalArgumentException("mysql connection limits must be positive");
        }
    }
}
