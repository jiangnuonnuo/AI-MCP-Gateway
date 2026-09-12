package cn.bugstack.ai.infrastructure.adapter.port;

import cn.bugstack.ai.domain.mysql.adapter.port.ISqlSafetyPort;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlQueryPolicy;
import cn.bugstack.ai.domain.mysql.model.valobj.SqlSafetyDecision;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.statement.Statement;

/**
 * MySQL 只读 SQL 责任链。每个节点都必须通过后才允许交给 PreparedStatement；任一解析不确定性
 * 都会返回稳定拒绝结果，调用方不得降级为原始 SQL 执行。
 */
@Component
public class MysqlSqlSafetyChain implements ISqlSafetyPort {
    private static final Set<String> WRITE_OR_DDL = Set.of(
            "INSERT", "UPDATE", "DELETE", "REPLACE", "MERGE", "CREATE", "ALTER", "DROP", "TRUNCATE",
            "RENAME", "GRANT", "REVOKE", "SET", "COMMIT", "ROLLBACK", "SAVEPOINT", "RELEASE",
            "START", "BEGIN", "LOCK", "UNLOCK", "CALL", "LOAD", "HANDLER", "ANALYZE", "OPTIMIZE",
            "REPAIR", "PURGE", "KILL", "RESET");
    private static final Set<String> DANGEROUS_FUNCTIONS = Set.of(
            "LOAD_FILE", "SLEEP", "BENCHMARK", "GET_LOCK", "RELEASE_LOCK", "IS_FREE_LOCK",
            "IS_USED_LOCK", "MASTER_POS_WAIT");

    private final MysqlSqlParser parser;
    private final MysqlAstSqlParser astParser;

    public MysqlSqlSafetyChain() {
        this(new MysqlSqlParser(), new MysqlAstSqlParser());
    }

    public MysqlSqlSafetyChain(MysqlSqlParser parser) {
        this(parser, new MysqlAstSqlParser());
    }

    public MysqlSqlSafetyChain(MysqlSqlParser parser, MysqlAstSqlParser astParser) {
        this.parser = parser;
        this.astParser = astParser;
    }

    public static MysqlSqlSafetyChain defaultChain() {
        return new MysqlSqlSafetyChain();
    }

    @Override
    public SqlSafetyDecision validate(String sql, Map<String, ?> parameters, MysqlQueryPolicy policy) {
        if (policy == null || !policy.isReadOnly()) {
            return SqlSafetyDecision.policyNotConfigured("read-only SQL policy is required");
        }
        if (sql == null || sql.length() > policy.getMaxSqlLength()) {
            return SqlSafetyDecision.rejected("SQL length exceeds policy");
        }

        final MysqlSqlParser.ParsedSql parsed;
        try {
            parsed = parser.parse(sql);
        } catch (RuntimeException e) {
            return SqlSafetyDecision.parseError("SQL cannot be parsed");
        }
        List<MysqlSqlParser.Token> tokens = parsed.tokens();
        if (parsed.semicolonCount() > 1 || (parsed.semicolonCount() == 1
                && !";".equals(tokens.get(tokens.size() - 1).text()))) {
            return SqlSafetyDecision.rejected("multiple SQL statements are not allowed");
        }
        String first = firstWord(tokens);
        SqlSafetyDecision lexicalDecision = rejectKnownSideEffects(tokens);
        if (lexicalDecision != null) return lexicalDecision;
        final Statement ast;
        try {
            ast = astParser.parseSingle(sql);
        } catch (JSQLParserException | RuntimeException e) {
            if (WRITE_OR_DDL.contains(first)) {
                return SqlSafetyDecision.rejected("statement has a write or control operation");
            }
            return SqlSafetyDecision.parseError("SQL cannot be parsed");
        }
        if (!(ast instanceof net.sf.jsqlparser.statement.select.Select)) {
            return SqlSafetyDecision.rejected("only SELECT statements are allowed");
        }
        if (astParser.containsRowLock(ast)) {
            return SqlSafetyDecision.rejected("row locking is not allowed");
        }
        if (!("SELECT".equals(first) || "WITH".equals(first))) {
            return SqlSafetyDecision.rejected("only SELECT statements are allowed");
        }
        for (int i = 0; i < tokens.size(); i++) {
            String word = tokens.get(i).upper();
            if (WRITE_OR_DDL.contains(word)) {
                return SqlSafetyDecision.rejected("statement has a write or control operation");
            }
            if (DANGEROUS_FUNCTIONS.contains(word) && i + 1 < tokens.size() && "(".equals(tokens.get(i + 1).text())) {
                return SqlSafetyDecision.rejected("statement contains a restricted function");
            }
            if ("FOR".equals(word) && i + 1 < tokens.size() && "UPDATE".equals(tokens.get(i + 1).upper())) {
                return SqlSafetyDecision.rejected("row locking is not allowed");
            }
            if ("INTO".equals(word) && i + 1 < tokens.size()) {
                String target = tokens.get(i + 1).upper();
                if ("OUTFILE".equals(target) || "DUMPFILE".equals(target)) {
                    return SqlSafetyDecision.rejected("file output is not allowed");
                }
            }
        }
        SqlSafetyDecision parameterDecision = validateParameters(tokens, parsed, parameters == null ? Map.of() : parameters);
        if (!parameterDecision.isAllowed()) return parameterDecision;
        return SqlSafetyDecision.allowed();
    }

    private SqlSafetyDecision validateParameters(List<MysqlSqlParser.Token> tokens,
                                                 MysqlSqlParser.ParsedSql parsed,
                                                 Map<String, ?> parameters) {
        if (parsed.namedParameterCount() > 0 && parsed.positionalParameterCount() > 0) {
            return SqlSafetyDecision.rejected("named and positional parameters cannot be mixed");
        }
        if (parsed.positionalParameterCount() > 0 && parsed.positionalParameterCount() != parameters.size()) {
            return SqlSafetyDecision.rejected("positional parameter count does not match arguments");
        }
        Set<String> used = new HashSet<>();
        for (MysqlSqlParser.Token token : tokens) {
            if (token.kind() != MysqlSqlParser.Kind.SYMBOL || !token.text().startsWith(":")) continue;
            String name = token.text().substring(1);
            if (!used.add(name)) return SqlSafetyDecision.rejected("duplicate template parameter");
            if (!parameters.containsKey(name)) return SqlSafetyDecision.rejected("unbound template parameter");
        }
        if (used.size() != parameters.size()) return SqlSafetyDecision.rejected("undeclared template parameter");
        return SqlSafetyDecision.allowed();
    }

    private static String firstWord(List<MysqlSqlParser.Token> tokens) {
        for (MysqlSqlParser.Token token : tokens) {
            if (token.kind() == MysqlSqlParser.Kind.WORD) return token.upper();
        }
        return "";
    }

    private SqlSafetyDecision rejectKnownSideEffects(List<MysqlSqlParser.Token> tokens) {
        for (int i = 0; i < tokens.size(); i++) {
            String word = tokens.get(i).upper();
            if (DANGEROUS_FUNCTIONS.contains(word) && i + 1 < tokens.size()
                    && "(".equals(tokens.get(i + 1).text())) {
                return SqlSafetyDecision.rejected("statement contains a restricted function");
            }
            if ("FOR".equals(word) && i + 1 < tokens.size()
                    && "UPDATE".equals(tokens.get(i + 1).upper())) {
                return SqlSafetyDecision.rejected("row locking is not allowed");
            }
            if ("INTO".equals(word) && i + 1 < tokens.size()) {
                String target = tokens.get(i + 1).upper();
                if ("OUTFILE".equals(target) || "DUMPFILE".equals(target)) {
                    return SqlSafetyDecision.rejected("file output is not allowed");
                }
            }
        }
        return null;
    }
}
