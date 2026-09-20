package cn.bugstack.ai.types.security;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 控制面响应与日志共用的敏感字段过滤器。
 *
 * <p>该类只返回新对象，不修改调用方数据，避免在异常记录或响应序列化过程中把原始凭证
 * 再次带出。字段名匹配大小写不敏感，未知结构按字符串做最小化过滤。</p>
 */
public final class SensitiveDataSanitizer {

    private static final String MASK = "***";
    private static final Pattern JDBC_CREDENTIAL = Pattern.compile(
            "(?i)([?&;])(password|passwd|user|username|token|secret|authorization|api[_-]?key)=[^&;\\s]*");

    private SensitiveDataSanitizer() {
    }

    public static boolean isSensitiveField(String fieldName) {
        if (fieldName == null) return false;
        String name = fieldName.replaceAll("[^a-zA-Z]", "").toLowerCase(Locale.ROOT);
        return name.contains("password") || name.contains("passwd") || name.contains("token")
                || name.contains("authorization") || name.contains("ciphertext")
                || name.contains("nonce") || name.contains("encryptionkey")
                || name.contains("apikey") || name.contains("secret");
    }

    public static String maskJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) return jdbcUrl;
        String masked = JDBC_CREDENTIAL.matcher(jdbcUrl).replaceAll("$1$2=" + MASK);
        int authority = masked.indexOf("://");
        if (authority < 0) return masked.contains("***") ? masked : "jdbc:***";
        int path = masked.indexOf('/', authority + 3);
        String prefix = masked.substring(0, authority + 3);
        return path < 0 ? prefix + MASK : prefix + MASK + masked.substring(path);
    }

    public static boolean containsJdbcCredentials(String jdbcUrl) {
        return jdbcUrl != null && JDBC_CREDENTIAL.matcher(jdbcUrl).find();
    }

    public static String sanitize(String value) {
        if (value == null) return null;
        return JDBC_CREDENTIAL.matcher(value).replaceAll("$1$2=" + MASK);
    }

    public static Map<String, Object> sanitizeMap(Map<String, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (source == null) return result;
        source.forEach((key, value) -> result.put(key,
                isSensitiveField(key) ? MASK : sanitizeValue(value)));
        return result;
    }

    private static Object sanitizeValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> converted = new LinkedHashMap<>();
            map.forEach((key, item) -> converted.put(String.valueOf(key),
                    isSensitiveField(String.valueOf(key)) ? MASK : sanitizeValue(item)));
            return converted;
        }
        if (value instanceof List<?> list) {
            List<Object> converted = new ArrayList<>(list.size());
            list.forEach(item -> converted.add(sanitizeValue(item)));
            return converted;
        }
        return value instanceof String string ? sanitize(string) : value;
    }
}
