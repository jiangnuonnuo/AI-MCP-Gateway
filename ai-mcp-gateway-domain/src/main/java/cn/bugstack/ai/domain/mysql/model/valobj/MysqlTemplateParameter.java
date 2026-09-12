package cn.bugstack.ai.domain.mysql.model.valobj;

import java.util.Objects;

/** 一个模板参数的公开 schema。 */
public final class MysqlTemplateParameter {
    private final String name;
    private final MysqlParameterType type;
    private final boolean required;
    private final String description;

    public MysqlTemplateParameter(String name, MysqlParameterType type, boolean required, String description) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("parameter name must not be blank");
        this.name = name;
        this.type = Objects.requireNonNull(type, "type");
        this.required = required;
        this.description = description;
    }

    public MysqlTemplateParameter(String name, MysqlParameterType type, boolean required) {
        this(name, type, required, null);
    }

    public String getName() { return name; }
    public MysqlParameterType getType() { return type; }
    public boolean isRequired() { return required; }
    public String getDescription() { return description; }
}
