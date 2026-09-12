package cn.bugstack.ai.domain.mysql.model.valobj;

/** 模板生命周期状态。只有 PUBLISHED 模板可以执行。 */
public enum MysqlTemplateStatus {
    DRAFT,
    PUBLISHED,
    DISABLED,
    DEPRECATED
}
