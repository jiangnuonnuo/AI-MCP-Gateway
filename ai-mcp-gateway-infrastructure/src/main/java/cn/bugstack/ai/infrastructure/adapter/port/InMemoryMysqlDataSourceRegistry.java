package cn.bugstack.ai.infrastructure.adapter.port;

import cn.bugstack.ai.domain.mysql.adapter.port.IMysqlDataSourceRegistry;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlDataSourceConfig;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MVP 配置型 Registry。它只保存目标业务数仓配置，不接触 Gateway 控制库 DAO/PO/Mapper。
 */
@Component("mysqlDataSourceRegistry")
public class InMemoryMysqlDataSourceRegistry implements IMysqlDataSourceRegistry {
    private final Map<String, MysqlDataSourceConfig> dataSources = new ConcurrentHashMap<>();

    public void register(MysqlDataSourceConfig config) {
        if (config == null) throw new IllegalArgumentException("data source must not be null");
        dataSources.put(config.getId(), config);
    }

    public void disable(String datasourceRef) {
        updateStatus(datasourceRef, false);
    }

    public void enable(String datasourceRef) {
        updateStatus(datasourceRef, true);
    }

    @Override
    public Optional<MysqlDataSourceConfig> find(String datasourceRef) {
        if (datasourceRef == null || datasourceRef.isBlank()) return Optional.empty();
        return Optional.ofNullable(dataSources.get(datasourceRef));
    }

    public void remove(String datasourceRef) {
        if (datasourceRef != null) dataSources.remove(datasourceRef);
    }

    private void updateStatus(String datasourceRef, boolean enabled) {
        MysqlDataSourceConfig config = dataSources.get(datasourceRef);
        if (config == null) return;
        dataSources.put(datasourceRef, config.toBuilder().enabled(enabled).build());
    }
}
