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
import java.util.UUID;

/**
 * MySQL 只读执行命令。
 *
 * <p>数据源引用由已解析的模板或动态 Tool 绑定提供，客户端参数不能覆盖目标数据源。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MysqlQueryCommand {

    /** 模板模式的已发布协议；动态模式为空。 */
    private MysqlTemplate template;

    /** 动态模式的 SQL 文本；模板模式从 template 读取。 */
    private String sql;

    /** 动态模式固定的数据源引用；模板模式从 template 读取。 */
    private String datasourceRef;

    /** 模板声明或动态 SQL 的绑定参数值。 */
    private Map<String, Object> parameters;

    /** 调用方请求的资源策略，只允许收紧服务端上限。 */
    private MysqlQueryPolicy requestedPolicy;

    /** 查询关联标识，用于 MCP 响应和审计关联。 */
    private String queryId;

    public MysqlQueryCommand(MysqlTemplate template, Map<String, Object> parameters) {
        this(template, null, null, parameters, null, null);
        normalize();
    }

    public MysqlQueryCommand(String sql, String datasourceRef, Map<String, Object> parameters,
                             MysqlQueryPolicy requestedPolicy, String queryId) {
        this(null, sql, datasourceRef, parameters, requestedPolicy, queryId);
        normalize();
    }

    /**
     * 规范化执行命令中的不可变边界和查询标识。
     */
    public void normalize() {
        if (template == null) {
            if (sql == null || sql.isBlank()) throw new IllegalArgumentException("sql is required");
            if (datasourceRef == null || datasourceRef.isBlank()) {
                throw new IllegalArgumentException("datasourceRef is required");
            }
        } else {
            datasourceRef = template.getDatasourceRef();
            sql = template.getSql();
        }
        parameters = parameters == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
        queryId = queryId == null || queryId.isBlank() ? UUID.randomUUID().toString() : queryId;
    }
}
