package cn.bugstack.ai.infrastructure.adapter.port;

import java.util.ArrayList;
import java.util.List;

/**
 * 轻量 MySQL 词法解析器。它不会以字符串前缀判断 SQL，而是先完整扫描字符串、注释、标识符和
 * 符号，再由安全责任链基于 token 序列判断语句类型及副作用。无法完整扫描的输入失败关闭。
 */
public final class MysqlSqlParser {
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
}
