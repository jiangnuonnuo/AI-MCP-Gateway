package cn.bugstack.ai.infrastructure.mysql;

import java.util.Optional;

/**
 * Infrastructure 内部按数据源引用解析 MySQL 运行时连接设置的技术契约。
 *
 * <p>该契约不属于 Domain，也不是 App Config 的持久化模型；实现可以读取控制库并在返回前完成
 * 凭证解密和应用级技术上限合并。</p>
 */
public interface MysqlConnectionSettingsRegistry {

    /**
     * 按固定数据源引用获取连接设置。
     *
     * @param datasourceRef 数据源引用
     * @return 连接设置
     */
    Optional<MysqlConnectionSettings> findSettings(String datasourceRef);

    /**
     * 获取连接测试使用的设置。连接测试允许校验尚未启用的数据源。
     */
    default Optional<MysqlConnectionSettings> findSettingsForHealth(String datasourceRef) {
        return findSettings(datasourceRef);
    }
}
