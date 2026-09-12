package cn.bugstack.ai.domain.mysql.model.valobj;

/** 模板参数支持的有限类型集合。 */
public enum MysqlParameterType {
    STRING,
    INTEGER,
    LONG,
    DECIMAL,
    BOOLEAN,
    DATE,
    TIME,
    DATETIME,
    TIMESTAMP
}
