package cn.bugstack.ai.infrastructure.adapter.port;

import cn.bugstack.ai.domain.mysql.adapter.port.IMysqlQueryPort;
import cn.bugstack.ai.domain.mysql.model.command.MysqlQueryCommand;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlQueryPolicy;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlQueryResult;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplate;
import cn.bugstack.ai.infrastructure.mysql.MysqlConnectionSettings;
import cn.bugstack.ai.infrastructure.mysql.MysqlConnectionSettingsRegistry;
import cn.bugstack.ai.infrastructure.mysql.MysqlTemplateParameterBinder;
import cn.bugstack.ai.types.exception.MysqlQueryException;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import jakarta.annotation.Resource;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Types;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 独立的 MySQL/JDBC 适配器。每个业务数据源拥有独立 Hikari 连接池，且连接、Statement、ResultSet
 * 均在成功、异常和超时路径通过 try-with-resources 释放。
 */
public class MysqlJdbcGateway implements IMysqlQueryPort, AutoCloseable {
    @Resource
    private MysqlConnectionSettingsRegistry connectionSettingsRegistry;

    @Resource
    private cn.bugstack.ai.infrastructure.security.ISecretResolver secretResolver;

    @Resource(name = "mysqlTemplateParameterBinder")
    private MysqlTemplateParameterBinder parameterBinder;
    private Map<String, PoolHolder> pools = new ConcurrentHashMap<>();

    @Override
    public MysqlQueryResult execute(MysqlQueryCommand command) {
        if (command != null) command.normalize();
        MysqlTemplate template = command == null ? null : command.getTemplate();
        if (template == null) throw new MysqlQueryException("INVALID_ARGUMENT", "template is required");
        MysqlConnectionSettings dataSource = connectionSettingsRegistry.findSettings(template.getDatasourceRef())
                .orElseThrow(() -> new MysqlQueryException("DATASOURCE_UNAVAILABLE", "data source is unavailable"));
        MysqlQueryPolicy effectivePolicy = command.getRequestedPolicy();
        if (effectivePolicy == null) {
            effectivePolicy = new MysqlQueryPolicy(dataSource.getMaxSqlLength(), dataSource.getMaxRows(),
                    dataSource.getMaxResultBytes(), dataSource.getMaxColumns(), dataSource.getMaxQueryTimeoutMs(), true);
        }
        MysqlQueryPolicy technicalPolicy = new MysqlQueryPolicy(dataSource.getMaxSqlLength(), dataSource.getMaxRows(),
                dataSource.getMaxResultBytes(), dataSource.getMaxColumns(), dataSource.getMaxQueryTimeoutMs(), true);
        effectivePolicy = effectivePolicy.boundedBy(technicalPolicy);
        MysqlTemplateParameterBinder.BoundSql bound = parameterBinder.bind(template, command.getParameters());

        PoolHolder holder = poolFor(dataSource);
        boolean acquired = false;
        try {
            try {
                acquired = holder.concurrent.tryAcquire(
                        Math.min(effectivePolicy.getTimeoutMs(), dataSource.getConnectionTimeoutMs()),
                        TimeUnit.MILLISECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new MysqlQueryException("QUERY_CANCELLED", "query was interrupted", interrupted);
            }
            if (!acquired) throw new MysqlQueryException("RESOURCE_LIMIT_EXCEEDED", "query concurrency limit reached");
            return executeJdbc(holder.pool, bound, command.getQueryId(), effectivePolicy);
        } catch (MysqlQueryException e) {
            throw e;
        } catch (SQLTimeoutException e) {
            throw new MysqlQueryException("QUERY_TIMEOUT", "query timed out", e);
        } catch (SQLException e) {
            String code = e.getSQLState() != null && (e.getSQLState().startsWith("08"))
                    ? "DATASOURCE_UNAVAILABLE" : "MYSQL_EXECUTION_ERROR";
            throw new MysqlQueryException(code, "MySQL query failed", e);
        } finally {
            if (acquired) holder.concurrent.release();
        }
    }

    public boolean health(String datasourceRef) {
        MysqlConnectionSettings config = connectionSettingsRegistry.findSettings(datasourceRef).orElse(null);
        if (config == null) return false;
        PoolHolder holder = poolFor(config);
        try (Connection connection = holder.pool.getConnection()) {
            return connection.isValid((int) Math.max(1, config.getValidationTimeoutMs() / 1_000));
        } catch (SQLException e) {
            return false;
        }
    }

    public Map<String, Map<String, Integer>> poolMetrics() {
        Map<String, Map<String, Integer>> metrics = new LinkedHashMap<>();
        pools.forEach((key, holder) -> {
            HikariPoolMXBean bean = holder.pool.getHikariPoolMXBean();
            if (bean != null) metrics.put(key, Map.of("active", bean.getActiveConnections(),
                    "idle", bean.getIdleConnections(), "total", bean.getTotalConnections(),
                    "waiting", bean.getThreadsAwaitingConnection()));
        });
        return metrics;
    }

    private MysqlQueryResult executeJdbc(HikariDataSource pool, MysqlTemplateParameterBinder.BoundSql bound,
                                         String queryId, MysqlQueryPolicy policy) throws SQLException {
        try (Connection connection = pool.getConnection()) {
            connection.setReadOnly(true);
            try (PreparedStatement statement = connection.prepareStatement(bound.sql())) {
                statement.setQueryTimeout((int) Math.max(1, Duration.ofMillis(policy.getTimeoutMs()).toSeconds()));
                statement.setMaxRows(policy.getMaxRows() + 1);
                bind(statement, bound.values());
                try (ResultSet resultSet = statement.executeQuery()) {
                    ResultSetMetaData metadata = resultSet.getMetaData();
                    if (metadata.getColumnCount() > policy.getMaxColumns()) {
                        throw new MysqlQueryException("RESULT_LIMIT_EXCEEDED", "column limit exceeded");
                    }
                    List<MysqlQueryResult.MysqlColumn> columns = new ArrayList<>();
                    for (int i = 1; i <= metadata.getColumnCount(); i++) {
                        columns.add(new MysqlQueryResult.MysqlColumn(metadata.getColumnName(i), metadata.getColumnLabel(i),
                                logicalType(metadata.getColumnType(i), metadata.getColumnTypeName(i))));
                    }
                    List<Map<String, Object>> rows = new ArrayList<>();
                    long bytes = 0;
                    boolean truncated = false;
                    while (resultSet.next()) {
                        if (rows.size() >= policy.getMaxRows()) { truncated = true; break; }
                        Map<String, Object> row = new LinkedHashMap<>();
                        long rowBytes = 0;
                        for (int i = 1; i <= metadata.getColumnCount(); i++) {
                            Object value = resultSet.getObject(i);
                            if (value instanceof BigDecimal decimal) value = decimal.stripTrailingZeros();
                            row.put(metadata.getColumnLabel(i), value);
                            rowBytes += value == null ? 4 : String.valueOf(value).length();
                        }
                        if (bytes + rowBytes > policy.getMaxResultBytes()) { truncated = true; break; }
                        rows.add(row);
                        bytes += rowBytes;
                    }
                    MysqlQueryResult result = new MysqlQueryResult(queryId, columns, rows, truncated, bytes);
                    result.normalize();
                    return result;
                }
            }
        }
    }

    private static void bind(PreparedStatement statement, List<Object> values) throws SQLException {
        for (int i = 0; i < values.size(); i++) {
            Object value = values.get(i);
            if (value == null) statement.setNull(i + 1, Types.NULL);
            else statement.setObject(i + 1, value);
        }
    }

    /** 将 JDBC 元数据收敛为稳定的逻辑类型，避免 Domain/MCP 暴露驱动编号。 */
    private static String logicalType(int sqlType, String typeName) {
        return switch (sqlType) {
            case Types.BIT, Types.BOOLEAN -> "BOOLEAN";
            case Types.TINYINT -> "TINYINT";
            case Types.SMALLINT -> "SMALLINT";
            case Types.INTEGER -> "INT";
            case Types.BIGINT -> "BIGINT";
            case Types.FLOAT, Types.REAL -> "FLOAT";
            case Types.DOUBLE -> "DOUBLE";
            case Types.NUMERIC, Types.DECIMAL -> "DECIMAL";
            case Types.DATE -> "DATE";
            case Types.TIME, Types.TIME_WITH_TIMEZONE -> "TIME";
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> "DATETIME";
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> "BINARY";
            case Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR, Types.NCHAR, Types.NVARCHAR,
                    Types.LONGNVARCHAR, Types.CLOB, Types.NCLOB -> "VARCHAR";
            case Types.NULL -> "NULL";
            default -> typeName == null || typeName.isBlank() ? "UNKNOWN" : typeName.toUpperCase();
        };
    }

    private PoolHolder poolFor(MysqlConnectionSettings config) {
        return pools.compute(config.getId(), (id, existing) -> {
            if (existing != null && existing.matches(config)) return existing;
            if (existing != null) existing.pool.close();
            HikariConfig hikari = new HikariConfig();
            hikari.setPoolName("mysql-" + id.replaceAll("[^A-Za-z0-9_-]", "_"));
            hikari.setJdbcUrl(config.getJdbcUrl());
            hikari.setUsername(config.getUsername());
            String password = config.getRuntimePassword();
            if (password == null || password.isBlank()) password = secretResolver.resolve(config.getPasswordSecretRef());
            if (password != null) hikari.setPassword(password);
            hikari.setMaximumPoolSize(config.getMaxPoolSize());
            hikari.setMinimumIdle(0);
            hikari.setConnectionTimeout(config.getConnectionTimeoutMs());
            hikari.setValidationTimeout(config.getValidationTimeoutMs());
            hikari.setInitializationFailTimeout(-1);
            hikari.setReadOnly(true);
            hikari.setConnectionTestQuery("SELECT 1");
            return new PoolHolder(new HikariDataSource(hikari), new Semaphore(config.getMaxConcurrentQueries()), config);
        });
    }

    @Override
    public void close() {
        pools.values().forEach(holder -> holder.pool.close());
        pools.clear();
    }

    private record PoolHolder(HikariDataSource pool, Semaphore concurrent, MysqlConnectionSettings config) {
        private boolean matches(MysqlConnectionSettings other) {
            return config.getJdbcUrl().equals(other.getJdbcUrl()) && config.getUsername().equals(other.getUsername())
                    && config.getMaxPoolSize() == other.getMaxPoolSize();
        }
    }
}
