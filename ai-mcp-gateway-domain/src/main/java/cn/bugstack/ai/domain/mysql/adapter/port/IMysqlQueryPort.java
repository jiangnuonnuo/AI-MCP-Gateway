package cn.bugstack.ai.domain.mysql.adapter.port;

import cn.bugstack.ai.domain.mysql.model.command.MysqlQueryCommand;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlExecutionTrace;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlQueryResult;

/** Domain 对 MySQL 只读执行能力的端口。 */
public interface IMysqlQueryPort {
    MysqlQueryResult execute(MysqlQueryCommand command);

    /**
     * 执行并反馈技术阶段。旧适配器仍可只实现 execute，默认实现会把端口边界包成可观察阶段。
     */
    default MysqlQueryResult executeWithTrace(MysqlQueryCommand command, MysqlExecutionTrace trace) {
        trace.start("DATASOURCE_CONNECTION");
        trace.succeed("DATASOURCE_CONNECTION");
        trace.start("SQL_EXECUTION");
        try {
            MysqlQueryResult result = execute(command);
            trace.succeed("SQL_EXECUTION");
            trace.start("RESPONSE_ASSEMBLY");
            trace.succeed("RESPONSE_ASSEMBLY");
            return result;
        } catch (RuntimeException exception) {
            trace.fail("SQL_EXECUTION", "MYSQL_EXECUTION_ERROR", "MySQL query failed");
            throw exception;
        }
    }
}
