package cn.bugstack.ai.domain.mysql.model.admin;

import java.util.Date;

/** Gateway Tool 与 MySQL 模板绑定查询快照。 */
public record MysqlBindingAdminView(
        Long id,
        String gatewayId,
        Long toolId,
        String toolName,
        String toolType,
        String toolDescription,
        String toolVersion,
        Long protocolId,
        String protocolType,
        Integer status,
        Date createTime,
        Date updateTime) {
}
