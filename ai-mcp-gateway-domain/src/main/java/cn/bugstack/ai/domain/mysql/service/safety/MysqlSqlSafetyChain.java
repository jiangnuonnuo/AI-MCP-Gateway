package cn.bugstack.ai.domain.mysql.service.safety;

import cn.bugstack.ai.domain.mysql.adapter.port.ISqlAnalysisPort;
import cn.bugstack.ai.domain.mysql.adapter.port.ISqlSafetyPort;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlQueryPolicy;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlSqlAnalysis;
import cn.bugstack.ai.domain.mysql.model.valobj.SqlSafetyDecision;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MySQL 只读 SQL 领域责任链。规则只表达业务治理，SQL Parser 通过端口提供技术分析事实。
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

    @Resource(name = "mysqlSqlParser")
    private ISqlAnalysisPort analysisPort;

    private List<ISqlSafetyRule> rules;

    /** 显式组装规则顺序，不能依赖 Bean 扫描顺序。 */
    @PostConstruct
    public void init() {
        rules = List.of(this::checkPolicy, this::analyzeSql, this::checkSingleStatement,
                this::checkReadOnlyStatement, this::checkSideEffects, this::checkParameters,
                this::checkResourcePolicy);
    }

    /** 为测试或非 Spring 场景提供空链实例；生产环境由容器注入 Parser。 */
    public static MysqlSqlSafetyChain defaultChain() {
        MysqlSqlSafetyChain chain = new MysqlSqlSafetyChain();
        chain.init();
        return chain;
    }

    @Override
    public SqlSafetyDecision validate(String sql, Map<String, ?> parameters, MysqlQueryPolicy policy) {
        if (rules == null) init();
        MysqlSqlSafetyContext context = MysqlSqlSafetyContext.builder()
                .sql(sql)
                .parameters(parameters == null ? Map.of() : parameters)
                .policy(policy)
                .build();
        for (ISqlSafetyRule rule : rules) {
            SqlSafetyDecision decision = rule.check(context);
            if (decision != null && decision.getStatus() != SqlSafetyDecision.Status.CONTINUE) {
                return decision;
            }
        }
        return SqlSafetyDecision.allowed();
    }

    private SqlSafetyDecision checkPolicy(MysqlSqlSafetyContext context) {
        MysqlQueryPolicy policy = context.getPolicy();
        if (policy == null || !policy.isReadOnly()) {
            return SqlSafetyDecision.policyNotConfigured("read-only SQL policy is required");
        }
        try {
            policy.validate();
        } catch (IllegalArgumentException e) {
            return SqlSafetyDecision.policyNotConfigured("read-only SQL policy is invalid");
        }
        if (context.getSql() == null || context.getSql().length() > policy.getMaxSqlLength()) {
            return SqlSafetyDecision.rejected("SQL length exceeds policy");
        }
        return SqlSafetyDecision.continueDecision();
    }

    private SqlSafetyDecision analyzeSql(MysqlSqlSafetyContext context) {
        if (analysisPort == null) {
            return SqlSafetyDecision.parseError("SQL analysis port is unavailable");
        }
        try {
            MysqlSqlAnalysis analysis = analysisPort.analyze(context.getSql());
            context.setAnalysis(analysis);
            return analysis == null
                    ? SqlSafetyDecision.parseError("SQL cannot be parsed")
                    : SqlSafetyDecision.continueDecision();
        } catch (RuntimeException e) {
            return SqlSafetyDecision.parseError("SQL cannot be parsed");
        }
    }

    private SqlSafetyDecision checkSingleStatement(MysqlSqlSafetyContext context) {
        MysqlSqlAnalysis analysis = context.getAnalysis();
        List<MysqlSqlAnalysis.Token> tokens = analysis.getTokens();
        if (analysis.getSemicolonCount() > 1 || (analysis.getSemicolonCount() == 1
                && (tokens.isEmpty() || !";".equals(tokens.get(tokens.size() - 1).getText())))) {
            return SqlSafetyDecision.rejected("multiple SQL statements are not allowed");
        }
        return SqlSafetyDecision.continueDecision();
    }

    private SqlSafetyDecision checkReadOnlyStatement(MysqlSqlSafetyContext context) {
        MysqlSqlAnalysis analysis = context.getAnalysis();
        if (!analysis.isSelectStatement()
                || !("SELECT".equals(analysis.getFirstWord()) || "WITH".equals(analysis.getFirstWord()))) {
            return SqlSafetyDecision.rejected("only SELECT statements are allowed");
        }
        if (analysis.isRowLock()) {
            return SqlSafetyDecision.rejected("row locking is not allowed");
        }
        return SqlSafetyDecision.continueDecision();
    }

    private SqlSafetyDecision checkSideEffects(MysqlSqlSafetyContext context) {
        List<MysqlSqlAnalysis.Token> tokens = context.getAnalysis().getTokens();
        for (int i = 0; i < tokens.size(); i++) {
            String word = tokens.get(i).upper();
            if (WRITE_OR_DDL.contains(word)) {
                return SqlSafetyDecision.rejected("statement has a write or control operation");
            }
            if (DANGEROUS_FUNCTIONS.contains(word) && i + 1 < tokens.size()
                    && "(".equals(tokens.get(i + 1).getText())) {
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
        return SqlSafetyDecision.continueDecision();
    }

    private SqlSafetyDecision checkParameters(MysqlSqlSafetyContext context) {
        MysqlSqlAnalysis analysis = context.getAnalysis();
        Map<String, ?> parameters = context.getParameters() == null ? Map.of() : context.getParameters();
        if (analysis.getNamedParameterCount() > 0 && analysis.getPositionalParameterCount() > 0) {
            return SqlSafetyDecision.rejected("named and positional parameters cannot be mixed");
        }
        if (analysis.getPositionalParameterCount() > 0
                && analysis.getPositionalParameterCount() != parameters.size()) {
            return SqlSafetyDecision.rejected("positional parameter count does not match arguments");
        }
        Set<String> used = new HashSet<>();
        for (MysqlSqlAnalysis.Token token : analysis.getTokens()) {
            if (token.getKind() != MysqlSqlAnalysis.Kind.SYMBOL || token.getText() == null
                    || !token.getText().startsWith(":")) {
                continue;
            }
            String name = token.getText().substring(1);
            if (!used.add(name)) return SqlSafetyDecision.rejected("duplicate template parameter");
            if (!parameters.containsKey(name)) return SqlSafetyDecision.rejected("unbound template parameter");
        }
        if (used.size() != parameters.size()) {
            return SqlSafetyDecision.rejected("undeclared template parameter");
        }
        return SqlSafetyDecision.continueDecision();
    }

    private SqlSafetyDecision checkResourcePolicy(MysqlSqlSafetyContext context) {
        MysqlQueryPolicy policy = context.getPolicy();
        if (policy.getMaxRows() <= 0 || policy.getMaxResultBytes() <= 0
                || policy.getMaxColumns() <= 0 || policy.getTimeoutMs() <= 0) {
            return SqlSafetyDecision.policyNotConfigured("resource limits are invalid");
        }
        return SqlSafetyDecision.continueDecision();
    }
}
