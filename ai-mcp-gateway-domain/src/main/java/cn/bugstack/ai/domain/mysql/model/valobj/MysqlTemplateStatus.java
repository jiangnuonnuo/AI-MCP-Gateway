package cn.bugstack.ai.domain.mysql.model.valobj;

/** MySQL 协议生命周期状态。控制面只允许启用和停用两态。 */
public enum MysqlTemplateStatus {
    ENABLED,
    DISABLED
}
