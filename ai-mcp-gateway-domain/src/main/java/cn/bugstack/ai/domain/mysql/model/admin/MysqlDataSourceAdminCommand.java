package cn.bugstack.ai.domain.mysql.model.admin;

/** 数据源管理命令；密码只在写入链路中存在。 */
public record MysqlDataSourceAdminCommand(
        Long id,
        String datasourceRef,
        String datasourceName,
        String datasourceType,
        String jdbcUrl,
        String username,
        String password,
        String encryptionKeyRef,
        Integer status) {
}
