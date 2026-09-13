package cn.bugstack.ai.infrastructure.adapter.port;

import cn.bugstack.ai.domain.mysql.adapter.port.ISqlAnalysisPort;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlSqlAnalysis;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 轻量 MySQL 词法解析器。它不会以字符串前缀判断 SQL，而是先完整扫描字符串、注释、标识符和
 * 符号，再由安全责任链基于 token 序列判断语句类型及副作用。无法完整扫描的输入失败关闭。
 */
@Component("mysqlSqlParser")
public final class MysqlSqlParser implements ISqlAnalysisPort {
    public enum Kind { WORD, STRING, SYMBOL, NUMBER }
    public record Token(Kind kind, String text) {
        public String upper() { return text.toUpperCase(java.util.Locale.ROOT); }
    }
    public record ParsedSql(List<Token> tokens, int semicolonCount, int namedParameterCount, int positionalParameterCount) {
        public ParsedSql {
            tokens = List.copyOf(tokens);
        }
    }

    public ParsedSql parse(String sql) {
        if (sql == null || sql.isBlank()) throw new IllegalArgumentException("SQL is blank");
        List<Token> tokens = new ArrayList<>();
        int semicolons = 0;
        int named = 0;
        int positional = 0;
        int i = 0;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            if (Character.isWhitespace(c)) { i++; continue; }
            if (c == '-' && i + 1 < sql.length() && sql.charAt(i + 1) == '-') {
                i += 2;
                while (i < sql.length() && sql.charAt(i) != '\n') i++;
                continue;
            }
            if (c == '#') {
                i++;
                while (i < sql.length() && sql.charAt(i) != '\n') i++;
                continue;
            }
            if (c == '/' && i + 1 < sql.length() && sql.charAt(i + 1) == '*') {
                int end = sql.indexOf("*/", i + 2);
                if (end < 0) throw new IllegalArgumentException("unterminated SQL comment");
                i = end + 2;
                continue;
            }
            if (c == '\'' || c == '"' || c == '`') {
                char quote = c;
                int start = i++;
                boolean closed = false;
                while (i < sql.length()) {
                    char current = sql.charAt(i++);
                    if (current == '\\' && quote != '`' && i < sql.length()) { i++; continue; }
                    if (current == quote) {
                        if (i < sql.length() && sql.charAt(i) == quote) { i++; continue; }
                        closed = true;
                        break;
                    }
                }
                if (!closed) throw new IllegalArgumentException("unterminated SQL literal");
                tokens.add(new Token(quote == '`' ? Kind.WORD : Kind.STRING, sql.substring(start + 1, i - 1)));
                continue;
            }
            if (c == ':') {
                int start = i++;
                if (i < sql.length() && Character.isJavaIdentifierStart(sql.charAt(i))) {
                    i++;
                    while (i < sql.length() && Character.isJavaIdentifierPart(sql.charAt(i))) i++;
                    tokens.add(new Token(Kind.SYMBOL, sql.substring(start, i)));
                    named++;
                } else {
                    tokens.add(new Token(Kind.SYMBOL, ":"));
                }
                continue;
            }
            if (c == '?') {
                tokens.add(new Token(Kind.SYMBOL, "?"));
                positional++;
                i++;
                continue;
            }
            if (Character.isLetter(c) || c == '_' || c == '$') {
                int start = i++;
                while (i < sql.length()) {
                    char next = sql.charAt(i);
                    if (!(Character.isLetterOrDigit(next) || next == '_' || next == '$')) break;
                    i++;
                }
                tokens.add(new Token(Kind.WORD, sql.substring(start, i)));
                continue;
            }
            if (Character.isDigit(c) || (c == '.' && i + 1 < sql.length() && Character.isDigit(sql.charAt(i + 1)))) {
                int start = i++;
                while (i < sql.length() && (Character.isDigit(sql.charAt(i)) || ".eE+-".indexOf(sql.charAt(i)) >= 0)) i++;
                tokens.add(new Token(Kind.NUMBER, sql.substring(start, i)));
                continue;
            }
            if (c == ';') semicolons++;
            tokens.add(new Token(Kind.SYMBOL, String.valueOf(c)));
            i++;
        }
        if (tokens.isEmpty()) throw new IllegalArgumentException("SQL is blank");
        return new ParsedSql(tokens, semicolons, named, positional);
    }

    /**
     * 将技术解析结果转换为 Domain 可消费的逻辑分析事实。
     */
    @Override
    public MysqlSqlAnalysis analyze(String sql) {
        ParsedSql parsed = parse(sql);
        final Statement statement;
        try {
            statement = parseSingle(sql);
        } catch (JSQLParserException e) {
            if (!containsKnownUnsafeSelectSyntax(parsed)) {
                throw new IllegalArgumentException("SQL cannot be parsed", e);
            }
            return lexicalAnalysis(parsed);
        }
        return logicalAnalysis(parsed, statement);
    }

    private MysqlSqlAnalysis logicalAnalysis(ParsedSql parsed, Statement statement) {
        List<MysqlSqlAnalysis.Token> logicalTokens = parsed.tokens().stream()
                .map(token -> MysqlSqlAnalysis.Token.builder()
                        .kind(MysqlSqlAnalysis.Kind.valueOf(token.kind().name()))
                        .text(token.text())
                        .build())
                .toList();
        String firstWord = logicalTokens.stream()
                .filter(token -> token.getKind() == MysqlSqlAnalysis.Kind.WORD)
                .map(MysqlSqlAnalysis.Token::upper)
                .findFirst()
                .orElse("");
        return MysqlSqlAnalysis.builder()
                .tokens(logicalTokens)
                .semicolonCount(parsed.semicolonCount())
                .namedParameterCount(parsed.namedParameterCount())
                .positionalParameterCount(parsed.positionalParameterCount())
                .firstWord(firstWord)
                .selectStatement(statement instanceof Select)
                .rowLock(containsRowLock(statement))
                .build();
    }

    /**
     * 部分 MySQL 专属输出语法不被通用 AST 解析器支持，但仍应进入领域安全规则判定，不能因为
     * 方言解析差异而绕过 INTO OUTFILE/DUMPFILE 等副作用检查。
     */
    private MysqlSqlAnalysis lexicalAnalysis(ParsedSql parsed) {
        List<MysqlSqlAnalysis.Token> logicalTokens = parsed.tokens().stream()
                .map(token -> MysqlSqlAnalysis.Token.builder()
                        .kind(MysqlSqlAnalysis.Kind.valueOf(token.kind().name()))
                        .text(token.text())
                        .build())
                .toList();
        String firstWord = logicalTokens.stream()
                .filter(token -> token.getKind() == MysqlSqlAnalysis.Kind.WORD)
                .map(MysqlSqlAnalysis.Token::upper)
                .findFirst()
                .orElse("");
        return MysqlSqlAnalysis.builder()
                .tokens(logicalTokens)
                .semicolonCount(parsed.semicolonCount())
                .namedParameterCount(parsed.namedParameterCount())
                .positionalParameterCount(parsed.positionalParameterCount())
                .firstWord(firstWord)
                .selectStatement("SELECT".equals(firstWord) || "WITH".equals(firstWord))
                .rowLock(containsTokenPair(logicalTokens, "FOR", "UPDATE"))
                .build();
    }

    private static boolean containsKnownUnsafeSelectSyntax(ParsedSql parsed) {
        List<Token> tokens = parsed.tokens();
        if (parsed.semicolonCount() > 1
                || (parsed.semicolonCount() == 1 && !";".equals(tokens.get(tokens.size() - 1).text()))) {
            return true;
        }
        if (!tokens.isEmpty() && ("CALL".equals(tokens.get(0).upper())
                || "LOAD".equals(tokens.get(0).upper())
                || "HANDLER".equals(tokens.get(0).upper()))) {
            return true;
        }
        for (int i = 0; i + 1 < tokens.size(); i++) {
            if ("INTO".equals(tokens.get(i).upper())
                    && ("OUTFILE".equals(tokens.get(i + 1).upper())
                    || "DUMPFILE".equals(tokens.get(i + 1).upper()))) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsTokenPair(List<MysqlSqlAnalysis.Token> tokens, String first, String second) {
        for (int i = 0; i + 1 < tokens.size(); i++) {
            if (first.equals(tokens.get(i).upper()) && second.equals(tokens.get(i + 1).upper())) return true;
        }
        return false;
    }

    /**
     * 使用完整 AST 校验单条 SQL。词法扫描和 AST 校验放在同一适配器中，避免为一次安全判断
     * 引入两个公开的 Parser 基础设施类型。
     */
    public Statement parseSingle(String sql) throws JSQLParserException {
        Statements statements = CCJSqlParserUtil.parseStatements(sql);
        if (statements == null || statements.getStatements() == null || statements.getStatements().size() != 1) {
            throw new JSQLParserException("exactly one SQL statement is required");
        }
        return statements.getStatements().get(0);
    }

    public boolean containsRowLock(Statement statement) {
        if (!(statement instanceof Select select)) return true;
        if (select.getSelectBody() instanceof PlainSelect plainSelect) {
            return plainSelect.isForUpdate();
        }
        return false;
    }
}
