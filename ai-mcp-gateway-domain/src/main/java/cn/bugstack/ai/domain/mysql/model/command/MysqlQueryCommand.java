package cn.bugstack.ai.domain.mysql.model.command;

import cn.bugstack.ai.domain.mysql.model.valobj.MysqlQueryPolicy;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplate;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** 模板执行命令；数据源引用由模板提供，不能由客户端覆盖。 */
public final class MysqlQueryCommand {
    private final MysqlTemplate template;
    private final Map<String, Object> parameters;
    private final MysqlQueryPolicy requestedPolicy;
    private final String queryId;

    public MysqlQueryCommand(MysqlTemplate template, Map<String, Object> parameters,
                             MysqlQueryPolicy requestedPolicy, String queryId) {
        this.template = Objects.requireNonNull(template, "template");
        this.parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        this.requestedPolicy = requestedPolicy;
        this.queryId = queryId == null || queryId.isBlank() ? UUID.randomUUID().toString() : queryId;
    }

    public MysqlQueryCommand(MysqlTemplate template, Map<String, Object> parameters) {
        this(template, parameters, null, null);
    }

    public MysqlTemplate getTemplate() { return template; }
    public Map<String, Object> getParameters() { return parameters; }
    public MysqlQueryPolicy getRequestedPolicy() { return requestedPolicy; }
    public String getQueryId() { return queryId; }
}
