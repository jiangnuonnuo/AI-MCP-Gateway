package cn.bugstack.ai.domain.mysql.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 一个模板参数的公开 Schema。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MysqlTemplateParameter {

    /** 参数名称，同时对应 SQL 命名占位符。 */
    private String name;

    /** 参数允许的数据类型。 */
    private MysqlParameterType type;

    /** 是否为必填参数。 */
    private boolean required;

    /** 面向 MCP Client 的参数说明。 */
    private String description;

    public MysqlTemplateParameter(String name, MysqlParameterType type, boolean required) {
        this(name, type, required, null);
    }

    /**
     * 校验模板参数定义。
     */
    public void validate() {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("parameter name must not be blank");
        }
        if (type == null) {
            throw new IllegalArgumentException("parameter type must not be null");
        }
    }
}
