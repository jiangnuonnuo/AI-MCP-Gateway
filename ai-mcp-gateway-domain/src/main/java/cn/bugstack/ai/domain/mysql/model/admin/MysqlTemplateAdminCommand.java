package cn.bugstack.ai.domain.mysql.model.admin;

import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplateParameter;

import java.util.List;

/** MySQL 模板管理命令。 */
public record MysqlTemplateAdminCommand(
        Long protocolId,
        String version,
        String name,
        String description,
        String datasourceRef,
        String sql,
        List<MysqlTemplateParameter> parameters,
        Integer maxRows,
        Long maxResultBytes,
        Integer maxColumns,
        Integer timeoutMs,
        Integer status) {
}
