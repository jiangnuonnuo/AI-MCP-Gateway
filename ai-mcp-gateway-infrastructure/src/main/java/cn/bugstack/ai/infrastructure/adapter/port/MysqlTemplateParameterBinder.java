package cn.bugstack.ai.infrastructure.adapter.port;

import cn.bugstack.ai.domain.mysql.model.valobj.MysqlParameterType;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplate;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplateParameter;

import java.math.BigDecimal;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 将模板命名参数转换为 JDBC {@code ?}，并在创建 PreparedStatement 前完成严格校验。
 * 扫描会跳过字符串、标识符和 SQL 注释，因此参数值永远不会通过字符串拼接进入 SQL。
 */
public final class MysqlTemplateParameterBinder {
    public record BoundSql(String sql, List<Object> values) {
        public BoundSql {
            values = List.copyOf(values);
        }
    }

    public BoundSql bind(MysqlTemplate template, Map<String, ?> arguments) {
        Map<String, ?> params = arguments == null ? Map.of() : arguments;
        Map<String, MysqlTemplateParameter> definitions = new HashMap<>();
        for (MysqlTemplateParameter parameter : template.getParameters()) {
            if (definitions.put(parameter.getName(), parameter) != null) {
                throw new MysqlParameterException("SQL_PARAMETER_ERROR", "duplicate template definition");
            }
        }
        for (MysqlTemplateParameter parameter : template.getParameters()) {
            if (parameter.isRequired() && !params.containsKey(parameter.getName())) {
                throw new MysqlParameterException("SQL_PARAMETER_ERROR", "missing template parameter");
            }
        }
        for (String supplied : params.keySet()) {
            if (!definitions.containsKey(supplied)) {
                throw new MysqlParameterException("SQL_PARAMETER_ERROR", "undeclared template parameter");
            }
            Object value = params.get(supplied);
            if (!isType(value, definitions.get(supplied).getType())) {
                throw new MysqlParameterException("SQL_PARAMETER_ERROR", "template parameter type mismatch");
            }
        }

        String sql = template.getSql();
        StringBuilder bound = new StringBuilder(sql.length());
        List<Object> values = new ArrayList<>();
        Set<String> used = new HashSet<>();
        int i = 0;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            if (c == '\'' || c == '"' || c == '`') {
                int start = i++;
                char quote = c;
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
                if (!closed) throw new MysqlParameterException("SQL_PARAMETER_ERROR", "unterminated SQL literal");
                bound.append(sql, start, i);
                continue;
            }
            if (c == '-' && i + 1 < sql.length() && sql.charAt(i + 1) == '-') {
                int start = i;
                i += 2;
                while (i < sql.length() && sql.charAt(i) != '\n') i++;
                bound.append(sql, start, i);
                continue;
            }
            if (c == '#') {
                int start = i++;
                while (i < sql.length() && sql.charAt(i) != '\n') i++;
                bound.append(sql, start, i);
                continue;
            }
            if (c == '/' && i + 1 < sql.length() && sql.charAt(i + 1) == '*') {
                int start = i;
                int end = sql.indexOf("*/", i + 2);
                if (end < 0) throw new MysqlParameterException("SQL_PARAMETER_ERROR", "unterminated SQL comment");
                i = end + 2;
                bound.append(sql, start, i);
                continue;
            }
            if (c == ':' && i + 1 < sql.length() && Character.isJavaIdentifierStart(sql.charAt(i + 1))) {
                int start = ++i;
                i++;
                while (i < sql.length() && Character.isJavaIdentifierPart(sql.charAt(i))) i++;
                String name = sql.substring(start, i);
                if (!used.add(name) || !definitions.containsKey(name)) {
                    throw new MysqlParameterException("SQL_PARAMETER_ERROR", "invalid or repeated template parameter");
                }
                bound.append('?');
                values.add(params.get(name));
                continue;
            }
            bound.append(c);
            i++;
        }
        if (used.size() != params.size()) {
            throw new MysqlParameterException("SQL_PARAMETER_ERROR", "parameter is not referenced by template");
        }
        return new BoundSql(bound.toString(), values);
    }

    private boolean isType(Object value, MysqlParameterType type) {
        if (value == null) return true;
        return switch (type) {
            case STRING -> value instanceof CharSequence;
            case INTEGER -> value instanceof Byte || value instanceof Short || value instanceof Integer;
            case LONG -> value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long;
            case DECIMAL -> value instanceof BigDecimal || value instanceof Byte || value instanceof Short
                    || value instanceof Integer || value instanceof Long || value instanceof Float || value instanceof Double;
            case BOOLEAN -> value instanceof Boolean;
            case DATE, TIME, DATETIME, TIMESTAMP -> value instanceof CharSequence
                    || value instanceof java.util.Date || value instanceof TemporalAccessor;
        };
    }

    public static final class MysqlParameterException extends IllegalArgumentException {
        private final String code;

        public MysqlParameterException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String getCode() { return code; }
    }
}
