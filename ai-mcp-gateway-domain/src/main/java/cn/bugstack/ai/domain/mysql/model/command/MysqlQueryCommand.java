package cn.bugstack.ai.domain.mysql.model.command;

import cn.bugstack.ai.domain.mysql.model.valobj.MysqlQueryPolicy;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Objects;
import java.util.UUID;

/**
 * MySQL 模板执行命令。
 *
 * <p>数据源引用由模板提供，客户端参数不能覆盖目标数据源。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MysqlQueryCommand {

    /** 待执行的已发布模板。 */
    private MysqlTemplate template;

    /** 模板声明的参数值。 */
    private Map<String, Object> parameters;

    /** 调用方请求的资源策略，只允许收紧服务端上限。 */
    private MysqlQueryPolicy requestedPolicy;

    /** 查询关联标识，用于 MCP 响应和审计关联。 */
    private String queryId;

    public MysqlQueryCommand(MysqlTemplate template, Map<String, Object> parameters) {
        this(template, parameters, null, null);
        normalize();
    }

    /**
     * 规范化执行命令中的不可变边界和查询标识。
     */
    public void normalize() {
        template = Objects.requireNonNull(template, "template");
        parameters = parameters == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
        queryId = queryId == null || queryId.isBlank() ? UUID.randomUUID().toString() : queryId;
    }
}
