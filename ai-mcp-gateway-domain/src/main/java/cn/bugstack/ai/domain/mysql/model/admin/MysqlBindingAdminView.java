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
        Date updateTime,
        String executionMode,
        String datasourceRef,
        Integer maxRows,
        Long maxResultBytes,
        Integer maxColumns,
        Integer timeoutMs) {

    /** 保持模板绑定旧调用方的构造契约。 */
    public MysqlBindingAdminView(Long id, String gatewayId, Long toolId, String toolName, String toolType,
                                 String toolDescription, String toolVersion, Long protocolId, String protocolType,
                                 Integer status, Date createTime, Date updateTime) {
        this(id, gatewayId, toolId, toolName, toolType, toolDescription, toolVersion, protocolId, protocolType,
                status, createTime, updateTime, "TEMPLATE", null, null, null, null, null);
    }
}
