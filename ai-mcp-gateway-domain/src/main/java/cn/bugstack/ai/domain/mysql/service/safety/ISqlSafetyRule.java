package cn.bugstack.ai.domain.mysql.service.safety;

import cn.bugstack.ai.domain.mysql.model.valobj.SqlSafetyDecision;

/**
 * SQL 只读责任链中的单一业务规则。
 */
@FunctionalInterface
public interface ISqlSafetyRule {

    /**
     * 检查当前领域上下文；返回继续表示交给下一个规则。
     *
     * @param context 安全规则上下文
     * @return 继续或终止决策
     */
    SqlSafetyDecision check(MysqlSqlSafetyContext context);
}
