package cn.bugstack.ai.domain.mysql.model.admin;

/** 动态 SQL Tool 绑定命令；动态模式只绑定固定数据源和执行护栏，不保存 SQL 模板。 */
public record MysqlDynamicBindingAdminCommand(
        Long id,
        String gatewayId,
        Long toolId,
        String toolName,
        String toolType,
        String toolDescription,
        String toolVersion,
        String datasourceRef,
        Integer maxRows,
        Long maxResultBytes,
        Integer maxColumns,
        Integer timeoutMs,
        Integer status) {
}
