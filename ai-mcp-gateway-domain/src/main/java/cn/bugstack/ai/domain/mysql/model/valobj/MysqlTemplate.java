package cn.bugstack.ai.domain.mysql.model.valobj;

import java.util.List;
import java.util.Objects;

/** 已绑定固定数据源的只读 SQL 模板。 */
public final class MysqlTemplate {
    private final String id;
    private final String version;
    private final String name;
    private final String description;
    private final String datasourceRef;
    private final String sql;
    private final List<MysqlTemplateParameter> parameters;
    private final MysqlTemplateStatus status;
    private final MysqlQueryPolicy policy;

    public MysqlTemplate(String id, String version, String name, String description, String datasourceRef,
                         String sql, List<MysqlTemplateParameter> parameters,
                         MysqlTemplateStatus status, MysqlQueryPolicy policy) {
        this.id = requireText(id, "id");
        this.version = requireText(version, "version");
        this.name = requireText(name, "name");
        this.description = description;
        this.datasourceRef = requireText(datasourceRef, "datasourceRef");
        this.sql = requireText(sql, "sql");
        this.parameters = List.copyOf(Objects.requireNonNull(parameters, "parameters"));
        this.status = Objects.requireNonNull(status, "status");
        this.policy = policy == null ? MysqlQueryPolicy.defaults() : policy;
    }

    public String getId() { return id; }
    public String getVersion() { return version; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getDatasourceRef() { return datasourceRef; }
    public String getSql() { return sql; }
    public List<MysqlTemplateParameter> getParameters() { return parameters; }
    public MysqlTemplateStatus getStatus() { return status; }
    public MysqlQueryPolicy getPolicy() { return policy; }
    public boolean isPublished() { return status == MysqlTemplateStatus.PUBLISHED; }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
