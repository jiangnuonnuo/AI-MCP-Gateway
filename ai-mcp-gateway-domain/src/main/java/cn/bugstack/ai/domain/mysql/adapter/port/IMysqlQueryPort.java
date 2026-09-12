package cn.bugstack.ai.domain.mysql.adapter.port;

import cn.bugstack.ai.domain.mysql.model.command.MysqlQueryCommand;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlQueryResult;

/** Domain 对 MySQL 只读执行能力的端口。 */
public interface IMysqlQueryPort {
    MysqlQueryResult execute(MysqlQueryCommand command);
}
