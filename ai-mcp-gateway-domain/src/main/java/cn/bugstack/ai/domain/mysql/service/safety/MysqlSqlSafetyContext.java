package cn.bugstack.ai.domain.mysql.service.safety;

import cn.bugstack.ai.domain.mysql.model.valobj.MysqlQueryPolicy;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlSqlAnalysis;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * SQL 安全责任链上下文，保存本次领域判断所需的业务输入和分析事实。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MysqlSqlSafetyContext {

    /** 待判断 SQL。 */
    private String sql;

    /** 调用方提供的参数。 */
    private Map<String, ?> parameters;

    /** 已合并的查询治理策略。 */
    private MysqlQueryPolicy policy;

    /** Parser 提供的逻辑分析结果。 */
    private MysqlSqlAnalysis analysis;
}
