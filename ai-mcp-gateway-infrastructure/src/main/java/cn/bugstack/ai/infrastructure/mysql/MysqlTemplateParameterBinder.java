package cn.bugstack.ai.infrastructure.mysql;

import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplate;
import cn.bugstack.ai.types.exception.MysqlParameterException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 将模板命名参数转换为 JDBC {@code ?}，并在创建 PreparedStatement 前完成严格校验。
 * 扫描会跳过字符串、标识符和 SQL 注释，因此参数值永远不会通过字符串拼接进入 SQL。
 */
@Component("mysqlTemplateParameterBinder")
public class MysqlTemplateParameterBinder {
    public record BoundSql(String sql, List<Object> values) {
        public BoundSql {
            values = List.copyOf(values);
        }
    }

    public BoundSql bind(MysqlTemplate template, Map<String, ?> arguments) {
        Set<String> declared = new HashSet<>();
        template.getParameters().forEach(parameter -> {
            if (!declared.add(parameter.getName())) {
                throw new MysqlParameterException("SQL_PARAMETER_ERROR", "duplicate template definition");
            }
        });
        return bind(template.getSql(), arguments, declared);
    }

    /** 将动态 SQL 的命名参数绑定为 JDBC 位置参数。 */
    public BoundSql bind(String sql, Map<String, ?> arguments) {
        if (sql == null || sql.isBlank()) {
            throw new MysqlParameterException("SQL_PARAMETER_ERROR", "sql is required");
        }
        return bind(sql, arguments, Set.of());
    }

    private BoundSql bind(String sql, Map<String, ?> arguments, Set<String> declared) {
        Map<String, ?> params = arguments == null ? Map.of() : arguments;
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
                if (!used.add(name) || !params.containsKey(name)
                        || (!declared.isEmpty() && !declared.contains(name))) {
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

}
