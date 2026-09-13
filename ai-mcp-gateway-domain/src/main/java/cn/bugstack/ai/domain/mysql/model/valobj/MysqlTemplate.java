package cn.bugstack.ai.domain.mysql.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 已绑定固定数据源的只读 SQL 模板。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MysqlTemplate {

    /** 模板唯一标识。 */
    private String id;

    /** 模板版本。 */
    private String version;

    /** 对外展示名称。 */
    private String name;

    /** 对外展示描述。 */
    private String description;

    /** 固定绑定的数据源引用。 */
    private String datasourceRef;

    /** 服务端保存的 SQL 正文。 */
    private String sql;

    /** 模板公开参数 Schema。 */
    private List<MysqlTemplateParameter> parameters;

    /** 模板生命周期状态。 */
    private MysqlTemplateStatus status;

    /** 模板级只读和资源策略。 */
    private MysqlQueryPolicy policy;

    public boolean isPublished() {
        return status == MysqlTemplateStatus.PUBLISHED;
    }

    /**
     * 校验模板的关键字段，发布或执行前由 Registry/Executor 调用。
     */
    public void validate() {
        requireText(id, "id");
        requireText(version, "version");
        requireText(name, "name");
        requireText(datasourceRef, "datasourceRef");
        requireText(sql, "sql");
        if (parameters == null) {
            throw new IllegalArgumentException("parameters must not be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (policy == null) {
            policy = MysqlQueryPolicy.defaults();
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
