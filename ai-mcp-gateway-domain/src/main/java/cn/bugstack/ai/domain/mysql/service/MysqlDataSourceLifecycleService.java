package cn.bugstack.ai.domain.mysql.service;

import cn.bugstack.ai.domain.mysql.adapter.port.IMysqlDataSourceRegistry;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlDataSourceRef;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlDataSourceStatus;
import cn.bugstack.ai.types.exception.MysqlDomainException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * MySQL 数据源生命周期领域服务。
 */
@Service
public class MysqlDataSourceLifecycleService {

    @Resource(name = "mysqlDataSourceRegistry")
    private IMysqlDataSourceRegistry dataSourceRegistry;

    /** 启用数据源。 */
    public void enable(String datasourceRef) {
        transition(datasourceRef, MysqlDataSourceStatus.ENABLED);
    }

    /** 停用数据源。 */
    public void disable(String datasourceRef) {
        transition(datasourceRef, MysqlDataSourceStatus.DISABLED);
    }

    private void transition(String datasourceRef, MysqlDataSourceStatus status) {
        MysqlDataSourceRef dataSource = dataSourceRegistry.find(datasourceRef)
                .orElseThrow(() -> new MysqlDomainException("DATASOURCE_NOT_FOUND", "data source is not found"));
        dataSource.setStatus(status);
        dataSourceRegistry.save(dataSource);
    }
}
