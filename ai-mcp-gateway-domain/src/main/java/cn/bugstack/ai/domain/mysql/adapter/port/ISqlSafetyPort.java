package cn.bugstack.ai.domain.mysql.adapter.port;

import cn.bugstack.ai.domain.mysql.model.valobj.MysqlQueryPolicy;
import cn.bugstack.ai.domain.mysql.model.valobj.SqlSafetyDecision;

import java.util.Map;

/** SQL 安全责任链端口，解析失败必须失败关闭。 */
public interface ISqlSafetyPort {
    SqlSafetyDecision validate(String sql, Map<String, ?> parameters, MysqlQueryPolicy policy);
}
