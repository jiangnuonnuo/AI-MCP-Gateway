package cn.bugstack.ai.domain.mysql.adapter.port;

import cn.bugstack.ai.domain.mysql.model.valobj.MysqlDataSourceRef;

import java.util.Optional;

/**
 * 业务数仓数据源存取契约。
 *
 * <p>虽然历史名称保留为 Registry，但职责是面向数据源集合的查询、保存和删除；
 * Infrastructure 实现归入 {@code adapter.repository}。</p>
 */
public interface IMysqlDataSourceRegistry {
    Optional<MysqlDataSourceRef> find(String datasourceRef);

    void save(MysqlDataSourceRef dataSource);

    /** 删除指定数据源配置。 */
    void delete(String datasourceRef);

    default MysqlDataSourceRef requireEnabled(String datasourceRef) {
        return find(datasourceRef)
                .filter(MysqlDataSourceRef::isEnabled)
                .orElseThrow(() -> new IllegalStateException("DATASOURCE_UNAVAILABLE"));
    }
}
