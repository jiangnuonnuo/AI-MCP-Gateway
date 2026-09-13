package cn.bugstack.ai.domain.mysql.adapter.port;

import cn.bugstack.ai.domain.mysql.model.valobj.MysqlSqlAnalysis;

/**
 * SQL 逻辑分析端口。具体 SQL Parser 属于 Infrastructure，领域只依赖分析事实。
 */
public interface ISqlAnalysisPort {

    /**
     * 分析完整 SQL。
     *
     * @param sql SQL 文本
     * @return 逻辑分析结果
     */
    MysqlSqlAnalysis analyze(String sql);
}
