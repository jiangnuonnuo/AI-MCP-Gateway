package cn.bugstack.ai.domain.mysql.model.admin;

import java.util.Date;

/** 数据源管理查询快照，不含控制库敏感字段。 */
public record MysqlDataSourceAdminView(
        Long id,
        String datasourceRef,
        String datasourceName,
        String datasourceType,
        String jdbcUrlMasked,
        String username,
        Integer status,
        boolean passwordConfigured,
        Date createTime,
        Date updateTime) {
}
