package cn.bugstack.ai.types.config;

/**
 * 供应用配置向 MySQL 技术适配器传递连接参数的最小契约。
 *
 * <p>该接口不是领域模型，不参与业务规则；实际配置对象由 App Config 提供。</p>
 */
public interface MysqlConnectionSettings {

    /** 数据源唯一标识。 */
    String getId();

    /** JDBC 连接地址。 */
    String getJdbcUrl();

    /** JDBC 登录用户名。 */
    String getUsername();

    /** 密钥引用。 */
    String getPasswordSecretRef();

    /** 仅供运行时替身使用的密码。 */
    String getRuntimePassword();

    /** 连接池最大连接数。 */
    int getMaxPoolSize();

    /** 获取连接超时时间，单位毫秒。 */
    long getConnectionTimeoutMs();

    /** 连接校验超时时间，单位毫秒。 */
    long getValidationTimeoutMs();

    /** 最大并发查询数。 */
    int getMaxConcurrentQueries();

    /** 单条 SQL 最大长度。 */
    long getMaxSqlLength();

    /** 单次查询最大返回行数。 */
    int getMaxRows();

    /** 单次查询最大结果字节数。 */
    long getMaxResultBytes();

    /** 单次查询最大返回列数。 */
    int getMaxColumns();

    /** 单次查询最大执行时间，单位毫秒。 */
    long getMaxQueryTimeoutMs();
}
