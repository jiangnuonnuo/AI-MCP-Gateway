package cn.bugstack.ai.domain.mysql.model.admin;

import java.util.List;

/** 管理查询的稳定分页快照。 */
public record MysqlAdminPage<T>(List<T> items, long total, int page, int rows) {
    public MysqlAdminPage {
        items = items == null ? List.of() : List.copyOf(items);
        page = Math.max(page, 1);
        rows = Math.max(rows, 1);
    }
}
