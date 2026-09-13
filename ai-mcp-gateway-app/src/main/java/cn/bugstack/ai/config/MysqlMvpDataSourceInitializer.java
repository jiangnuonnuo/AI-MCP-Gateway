package cn.bugstack.ai.config;

import cn.bugstack.ai.domain.mysql.model.valobj.MysqlDataSourceRef;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlDataSourceStatus;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlQueryPolicy;
import cn.bugstack.ai.domain.mysql.adapter.port.IMysqlDataSourceRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 将应用启动属性转换为目标数仓的数据源领域引用。
 */
@Component
public class MysqlMvpDataSourceInitializer {

    @Resource
    private MysqlConnectionProperties properties;

    @Resource(name = "mysqlDataSourceRegistry")
    private IMysqlDataSourceRegistry registry;

    /** 只注册具有完整运行时用户名和连接参数的数据源。 */
    @PostConstruct
    public void registerRuntimeDataSource() {
        if (!properties.isMvpEnabled() || properties.getDatasource() == null) {
            return;
        }
        for (Map.Entry<String, MysqlDataSourceConfig> entry : properties.getDatasource().entrySet()) {
            MysqlDataSourceConfig config = entry.getValue();
            if (config == null) {
                continue;
            }
            if (config.getId() == null || config.getId().isBlank()) {
                config.setId(entry.getKey());
            }
            if (config.getUsername() == null || config.getUsername().isBlank()) {
                continue;
            }
            config.validate();
            registry.save(MysqlDataSourceRef.builder()
                    .id(config.getId())
                    .status(config.isEnabled() ? MysqlDataSourceStatus.ENABLED : MysqlDataSourceStatus.DISABLED)
                    .policy(new MysqlQueryPolicy(config.getMaxSqlLength(), config.getMaxRows(),
                            config.getMaxResultBytes(), config.getMaxColumns(), config.getMaxQueryTimeoutMs(), true))
                    .build());
        }
    }
}
