package cn.bugstack.ai.types.config;

import java.util.Optional;

/**
 * 应用启动配置向 Infrastructure 提供 MySQL 连接设置的适配契约。
 */
public interface MysqlConnectionSettingsRegistry {

    /**
     * 按固定数据源引用获取连接设置。
     *
     * @param datasourceRef 数据源引用
     * @return 连接设置
     */
    Optional<MysqlConnectionSettings> find(String datasourceRef);
}
