package cn.bugstack.ai.domain.mysql.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MySQL 数据源的领域引用。
 *
 * <p>该模型只表达数据源标识、业务状态和查询治理策略，不包含 JDBC 地址、账号、密钥或连接池
 * 等启动配置。</p>
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class MysqlDataSourceRef {

    /** 数据源唯一引用。 */
    private String id;

    /** 数据源业务状态。 */
    @Builder.Default
    private MysqlDataSourceStatus status = MysqlDataSourceStatus.DISABLED;

    /** 数据源级查询治理策略。 */
    private MysqlQueryPolicy policy;

    /** 判断数据源是否允许执行业务查询。 */
    public boolean isEnabled() {
        return status == MysqlDataSourceStatus.ENABLED;
    }

    /** 校验领域引用。 */
    public void validate() {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("data source id must not be blank");
        }
        if (status == null) {
            throw new IllegalArgumentException("data source status must not be null");
        }
        if (policy == null) {
            policy = MysqlQueryPolicy.defaults();
        }
        policy.validate();
    }
}
