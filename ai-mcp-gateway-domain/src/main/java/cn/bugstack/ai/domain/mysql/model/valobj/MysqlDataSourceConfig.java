package cn.bugstack.ai.domain.mysql.model.valobj;

import java.util.Objects;

/**
 * 连接业务数仓所需的运行时配置。
 * <p>
 * 密码只接受运行时注入值或 {@code env:NAME} 密钥引用，配置对象不会将密钥写入日志、MCP
 * schema 或控制库。数据源 ID 是模板绑定的一部分，客户端参数没有覆盖它的权限。
 */
public final class MysqlDataSourceConfig {

    private final String id;
    private final String jdbcUrl;
    private final String username;
    private final String passwordSecretRef;
    private final String runtimePassword;
    private final MysqlDataSourceStatus status;
    private final int maxPoolSize;
    private final long connectionTimeoutMs;
    private final long validationTimeoutMs;
    private final int maxConcurrentQueries;
    private final long maxSqlLength;
    private final int maxRows;
    private final long maxResultBytes;
    private final long maxQueryTimeoutMs;

    private MysqlDataSourceConfig(Builder builder) {
        this.id = requireText(builder.id, "id");
        this.jdbcUrl = requireText(builder.jdbcUrl, "jdbcUrl");
        this.username = requireText(builder.username, "username");
        this.passwordSecretRef = builder.passwordSecretRef;
        this.runtimePassword = builder.runtimePassword;
        this.status = Objects.requireNonNullElse(builder.status, MysqlDataSourceStatus.DISABLED);
        this.maxPoolSize = positive(builder.maxPoolSize, "maxPoolSize");
        this.connectionTimeoutMs = positive(builder.connectionTimeoutMs, "connectionTimeoutMs");
        this.validationTimeoutMs = positive(builder.validationTimeoutMs, "validationTimeoutMs");
        this.maxConcurrentQueries = positive(builder.maxConcurrentQueries, "maxConcurrentQueries");
        this.maxSqlLength = positive(builder.maxSqlLength, "maxSqlLength");
        this.maxRows = positive(builder.maxRows, "maxRows");
        this.maxResultBytes = positive(builder.maxResultBytes, "maxResultBytes");
        this.maxQueryTimeoutMs = positive(builder.maxQueryTimeoutMs, "maxQueryTimeoutMs");
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getId() { return id; }
    public String getJdbcUrl() { return jdbcUrl; }
    public String getUsername() { return username; }
    public String getPasswordSecretRef() { return passwordSecretRef; }
    public String getRuntimePassword() { return runtimePassword; }
    public MysqlDataSourceStatus getStatus() { return status; }
    public boolean isEnabled() { return status == MysqlDataSourceStatus.ENABLED; }
    public int getMaxPoolSize() { return maxPoolSize; }
    public long getConnectionTimeoutMs() { return connectionTimeoutMs; }
    public long getValidationTimeoutMs() { return validationTimeoutMs; }
    public int getMaxConcurrentQueries() { return maxConcurrentQueries; }
    public long getMaxSqlLength() { return maxSqlLength; }
    public int getMaxRows() { return maxRows; }
    public long getMaxResultBytes() { return maxResultBytes; }
    public long getMaxQueryTimeoutMs() { return maxQueryTimeoutMs; }

    public Builder toBuilder() {
        return builder().id(id).jdbcUrl(jdbcUrl).username(username)
                .passwordSecretRef(passwordSecretRef).runtimePassword(runtimePassword)
                .status(status).maxPoolSize(maxPoolSize).connectionTimeoutMs(connectionTimeoutMs)
                .validationTimeoutMs(validationTimeoutMs).maxConcurrentQueries(maxConcurrentQueries)
                .maxSqlLength(maxSqlLength).maxRows(maxRows).maxResultBytes(maxResultBytes)
                .maxQueryTimeoutMs(maxQueryTimeoutMs);
    }

    public static final class Builder {
        private String id;
        private String jdbcUrl;
        private String username;
        private String passwordSecretRef;
        private String runtimePassword;
        private MysqlDataSourceStatus status = MysqlDataSourceStatus.DISABLED;
        private int maxPoolSize = 5;
        private long connectionTimeoutMs = 5_000;
        private long validationTimeoutMs = 2_000;
        private int maxConcurrentQueries = 4;
        private long maxSqlLength = 64 * 1024;
        private int maxRows = 1_000;
        private long maxResultBytes = 4 * 1024 * 1024;
        private long maxQueryTimeoutMs = 30_000;

        public Builder id(String value) { this.id = value; return this; }
        public Builder jdbcUrl(String value) { this.jdbcUrl = value; return this; }
        public Builder username(String value) { this.username = value; return this; }
        public Builder passwordSecretRef(String value) { this.passwordSecretRef = value; return this; }
        /** 仅用于运行时测试替身；不得将此字段持久化。 */
        public Builder runtimePassword(String value) { this.runtimePassword = value; return this; }
        public Builder status(MysqlDataSourceStatus value) { this.status = value; return this; }
        public Builder enabled(boolean value) { this.status = value ? MysqlDataSourceStatus.ENABLED : MysqlDataSourceStatus.DISABLED; return this; }
        public Builder maxPoolSize(int value) { this.maxPoolSize = value; return this; }
        public Builder connectionTimeoutMs(long value) { this.connectionTimeoutMs = value; return this; }
        public Builder validationTimeoutMs(long value) { this.validationTimeoutMs = value; return this; }
        public Builder maxConcurrentQueries(int value) { this.maxConcurrentQueries = value; return this; }
        public Builder maxSqlLength(long value) { this.maxSqlLength = value; return this; }
        public Builder maxRows(int value) { this.maxRows = value; return this; }
        public Builder maxResultBytes(long value) { this.maxResultBytes = value; return this; }
        public Builder maxQueryTimeoutMs(long value) { this.maxQueryTimeoutMs = value; return this; }
        public MysqlDataSourceConfig build() { return new MysqlDataSourceConfig(this); }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }

    private static int positive(int value, String name) {
        if (value <= 0) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }

    private static long positive(long value, String name) {
        if (value <= 0) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }
}
