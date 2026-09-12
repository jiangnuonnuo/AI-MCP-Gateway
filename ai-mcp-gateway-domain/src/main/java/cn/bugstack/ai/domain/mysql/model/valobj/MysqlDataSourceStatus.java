package cn.bugstack.ai.domain.mysql.model.valobj;

/**
 * 目标 MySQL 数据源的发布状态。
 * 数据源状态属于领域配置，停用时执行器必须在获取连接前拒绝调用。
 */
public enum MysqlDataSourceStatus {
    ENABLED,
    DISABLED
}
