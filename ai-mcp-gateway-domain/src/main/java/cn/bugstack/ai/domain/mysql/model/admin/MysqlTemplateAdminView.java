package cn.bugstack.ai.domain.mysql.model.admin;

import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplateParameter;

import java.util.Date;
import java.util.List;

/** MySQL 模板管理查询快照。 */
public record MysqlTemplateAdminView(
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
        Integer status,
        Date createTime,
        Date updateTime) {
    public MysqlTemplateAdminView {
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
    }
}
