package cn.bugstack.ai.domain.mysql.adapter.port;

import cn.bugstack.ai.domain.mysql.model.valobj.MysqlDataSourceConfig;

import java.util.Optional;

/** 业务数仓数据源 Registry；实现可以是内存配置或后续控制库适配器。 */
public interface IMysqlDataSourceRegistry {
    Optional<MysqlDataSourceConfig> find(String datasourceRef);

    default MysqlDataSourceConfig requireEnabled(String datasourceRef) {
        return find(datasourceRef)
                .filter(MysqlDataSourceConfig::isEnabled)
                .orElseThrow(() -> new IllegalStateException("DATASOURCE_UNAVAILABLE"));
    }
}
