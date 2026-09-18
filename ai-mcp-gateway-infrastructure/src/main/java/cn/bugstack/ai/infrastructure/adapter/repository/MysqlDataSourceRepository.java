package cn.bugstack.ai.infrastructure.adapter.repository;

import cn.bugstack.ai.domain.mysql.adapter.port.IMysqlDataSourceRegistry;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlDataSourceRef;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlDataSourceStatus;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlQueryPolicy;
import cn.bugstack.ai.infrastructure.dao.IMcpDataSourceDao;
import cn.bugstack.ai.infrastructure.dao.po.McpDataSourcePO;
import cn.bugstack.ai.infrastructure.mysql.MysqlConnectionSettings;
import cn.bugstack.ai.infrastructure.mysql.MysqlConnectionSettingsRegistry;
import cn.bugstack.ai.infrastructure.security.DataSourceCredentialCipher;
import cn.bugstack.ai.types.config.MysqlRuntimeSettings;
import cn.bugstack.ai.types.exception.MysqlDomainException;
import cn.bugstack.ai.types.exception.MysqlQueryException;
import jakarta.annotation.Resource;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 控制库数据源仓储，同时为 JDBC 适配器提供脱敏后的运行时连接设置。
 */
@Repository("mysqlDataSourceRegistry")
public class MysqlDataSourceRepository implements IMysqlDataSourceRegistry, MysqlConnectionSettingsRegistry {

    @Resource
    private IMcpDataSourceDao dataSourceDao;

    @Resource
    private DataSourceCredentialCipher credentialCipher;

    @Resource
    private MysqlRuntimeSettings runtimeSettings;

    @Override
    public Optional<MysqlDataSourceRef> find(String datasourceRef) {
        if (datasourceRef == null || datasourceRef.isBlank()) return Optional.empty();
        try {
            return Optional.ofNullable(dataSourceDao.queryByDatasourceRef(datasourceRef)).map(this::toDomain);
        } catch (DataAccessException e) {
            throw new MysqlDomainException("DATASOURCE_PERSISTENCE_ERROR", "data source configuration is unavailable");
        }
    }

    @Override
    public void save(MysqlDataSourceRef dataSource) {
        if (dataSource == null || dataSource.getId() == null || dataSource.getId().isBlank()) {
            throw new MysqlDomainException("DATASOURCE_INVALID", "data source reference is invalid");
        }
        try {
            McpDataSourcePO current = dataSourceDao.queryByDatasourceRef(dataSource.getId());
            if (current == null) {
                throw new MysqlDomainException("DATASOURCE_NOT_FOUND", "data source is not found");
            }
            current.setStatus(dataSource.getStatus() == MysqlDataSourceStatus.ENABLED ? 1 : 0);
            dataSourceDao.updateById(current);
        } catch (MysqlDomainException e) {
            throw e;
        } catch (DataAccessException e) {
            throw new MysqlDomainException("DATASOURCE_PERSISTENCE_ERROR", "data source configuration could not be saved");
        }
    }

    @Override
    public void delete(String datasourceRef) {
        try {
            dataSourceDao.deleteByDatasourceRef(datasourceRef);
        } catch (DataAccessException e) {
            throw new MysqlDomainException("DATASOURCE_PERSISTENCE_ERROR", "data source configuration could not be deleted");
        }
    }

    @Override
    public Optional<MysqlConnectionSettings> findSettings(String datasourceRef) {
        if (datasourceRef == null || datasourceRef.isBlank()) return Optional.empty();
        McpDataSourcePO po;
        try {
            po = dataSourceDao.queryEnabledByDatasourceRef(datasourceRef);
        } catch (DataAccessException e) {
            throw new MysqlQueryException("DATASOURCE_UNAVAILABLE", "data source is unavailable", e);
        }
        if (po == null || !"mysql".equalsIgnoreCase(po.getDatasourceType())) return Optional.empty();
        if (po.getJdbcUrl() == null || po.getJdbcUrl().isBlank()
                || po.getJdbcUrl().matches("(?i).*([?&])password(=|%3d).*")) {
            throw new MysqlQueryException("DATASOURCE_CREDENTIAL_ERROR", "data source configuration is invalid");
        }
        String password = credentialCipher.decrypt(po.getPasswordCiphertext(), po.getPasswordNonce(),
                po.getEncryptionKeyRef());
        return Optional.of(new RuntimeSettings(po, password, runtimeSettings));
    }

    private MysqlDataSourceRef toDomain(McpDataSourcePO po) {
        return MysqlDataSourceRef.builder()
                .id(po.getDatasourceRef())
                .status(Integer.valueOf(1).equals(po.getStatus())
                        ? MysqlDataSourceStatus.ENABLED : MysqlDataSourceStatus.DISABLED)
                .policy(new MysqlQueryPolicy(runtimeSettings.getMaxSqlLength(), runtimeSettings.getMaxRows(),
                        runtimeSettings.getMaxResultBytes(), runtimeSettings.getMaxColumns(),
                        runtimeSettings.getMaxQueryTimeoutMs(), true))
                .build();
    }

    private static final class RuntimeSettings implements MysqlConnectionSettings {
        private final McpDataSourcePO source;
        private final String password;
        private final MysqlRuntimeSettings limits;

        private RuntimeSettings(McpDataSourcePO source, String password, MysqlRuntimeSettings limits) {
            this.source = source;
            this.password = password;
            this.limits = limits;
        }

        @Override public String getId() { return source.getDatasourceRef(); }
        @Override public String getJdbcUrl() { return source.getJdbcUrl(); }
        @Override public String getUsername() { return source.getUsername(); }
        @Override public String getPasswordSecretRef() { return null; }
        @Override public String getRuntimePassword() { return password; }
        @Override public int getMaxPoolSize() { return limits.getMaxPoolSize(); }
        @Override public long getConnectionTimeoutMs() { return limits.getConnectionTimeoutMs(); }
        @Override public long getValidationTimeoutMs() { return limits.getValidationTimeoutMs(); }
        @Override public int getMaxConcurrentQueries() { return limits.getMaxConcurrentQueries(); }
        @Override public long getMaxSqlLength() { return limits.getMaxSqlLength(); }
        @Override public int getMaxRows() { return limits.getMaxRows(); }
        @Override public long getMaxResultBytes() { return limits.getMaxResultBytes(); }
        @Override public int getMaxColumns() { return limits.getMaxColumns(); }
        @Override public long getMaxQueryTimeoutMs() { return limits.getMaxQueryTimeoutMs(); }
    }
}
