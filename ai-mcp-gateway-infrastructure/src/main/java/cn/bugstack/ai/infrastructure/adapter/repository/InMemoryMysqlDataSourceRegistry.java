package cn.bugstack.ai.infrastructure.adapter.repository;

import cn.bugstack.ai.domain.mysql.adapter.port.IMysqlDataSourceRegistry;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlDataSourceRef;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MVP 配置型 Registry。它只保存目标业务数仓配置，不接触 Gateway 控制库 DAO/PO/Mapper。
 */
@Repository("mysqlDataSourceRegistry")
public class InMemoryMysqlDataSourceRegistry implements IMysqlDataSourceRegistry {
    private Map<String, MysqlDataSourceRef> dataSources = new ConcurrentHashMap<>();

    public void register(MysqlDataSourceRef dataSource) {
        save(dataSource);
    }

    @Override
    public void save(MysqlDataSourceRef dataSource) {
        if (dataSource == null) throw new IllegalArgumentException("data source must not be null");
        dataSource.validate();
        dataSources.put(dataSource.getId(), dataSource);
    }

    @Override
    public Optional<MysqlDataSourceRef> find(String datasourceRef) {
        if (datasourceRef == null || datasourceRef.isBlank()) return Optional.empty();
        return Optional.ofNullable(dataSources.get(datasourceRef));
    }

    @Override
    public void delete(String datasourceRef) {
        if (datasourceRef != null) dataSources.remove(datasourceRef);
    }

}
