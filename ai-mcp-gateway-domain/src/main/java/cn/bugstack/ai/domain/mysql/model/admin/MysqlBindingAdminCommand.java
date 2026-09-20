package cn.bugstack.ai.domain.mysql.model.admin;

/** Gateway Tool 与 MySQL 模板绑定管理命令。 */
public record MysqlBindingAdminCommand(
        Long id,
        String gatewayId,
        Long toolId,
        String toolName,
        String toolType,
        String toolDescription,
        String toolVersion,
        Long protocolId,
        String protocolType,
        Integer status) {
}
