package cn.bugstack.ai.domain.tool.model.valobj;

/**
 * Tool 的后端类型。
 *
 * <p>后端类型是 Tool 配置的一部分，客户端只能调用已发布配置，不能在请求中选择后端。</p>
 */
public enum ToolBackendType {

    HTTP,
    MYSQL,
    UNKNOWN;

    /**
     * 将配置或外部输入转换为领域类型。无法识别的值必须保留为 UNKNOWN，避免默认落到某个后端。
     */
    public static ToolBackendType from(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        return switch (value.trim().toUpperCase()) {
            case "HTTP", "HTTP_REQUEST" -> HTTP;
            case "MYSQL", "MYSQL_JDBC", "MYSQL_TEMPLATE" -> MYSQL;
            default -> UNKNOWN;
        };
    }
}
