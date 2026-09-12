package cn.bugstack.ai.infrastructure.adapter.port;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;

/** JSqlParser 语法入口，作为 SQL 责任链的首个完整 AST 校验节点。 */
public final class MysqlAstSqlParser {
    public Statement parseSingle(String sql) throws JSQLParserException {
        Statements statements = CCJSqlParserUtil.parseStatements(sql);
        if (statements == null || statements.getStatements() == null || statements.getStatements().size() != 1) {
            throw new JSQLParserException("exactly one SQL statement is required");
        }
        Statement statement = statements.getStatements().get(0);
        return statement;
    }

    public boolean containsRowLock(Statement statement) {
        if (!(statement instanceof Select select)) return true;
        if (select.getSelectBody() instanceof PlainSelect plainSelect) {
            return plainSelect.isForUpdate();
        }
        return false;
    }
}
