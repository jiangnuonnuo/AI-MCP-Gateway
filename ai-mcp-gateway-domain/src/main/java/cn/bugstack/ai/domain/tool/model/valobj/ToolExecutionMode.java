package cn.bugstack.ai.domain.tool.model.valobj;

/**
 * Tool 的执行模式。后端类型与执行模式同时参与路由，避免把 MySQL 模板误当作通用 HTTP 请求。
 */
public enum ToolExecutionMode {

    HTTP_REQUEST,
    MYSQL_TEMPLATE,
    MYSQL_DYNAMIC_READONLY,
    UNKNOWN;

    public static ToolExecutionMode from(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        return switch (value.trim().toUpperCase()) {
            case "HTTP", "HTTP_REQUEST", "REQUEST" -> HTTP_REQUEST;
            case "MYSQL", "MYSQL_TEMPLATE", "TEMPLATE" -> MYSQL_TEMPLATE;
            case "MYSQL_DYNAMIC", "MYSQL_DYNAMIC_READONLY", "DYNAMIC_READONLY" -> MYSQL_DYNAMIC_READONLY;
            default -> UNKNOWN;
        };
    }
}
