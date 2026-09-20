package cn.bugstack.ai.domain.mysql.model.admin;

/** 管理分页过滤条件，边界由 Domain 统一收紧。 */
public final class MysqlAdminQueries {
    private MysqlAdminQueries() {
    }

    public record DataSource(String datasourceRef, String datasourceName, Integer status, int page, int rows) {
        public DataSource {
            page = normalizePage(page);
            rows = normalizeRows(rows);
        }
    }

    public record Template(Long protocolId, String name, String datasourceRef, Integer status, int page, int rows) {
        public Template {
            page = normalizePage(page);
            rows = normalizeRows(rows);
        }
    }

    public record Binding(String gatewayId, String toolName, Long protocolId, Integer status, int page, int rows) {
        public Binding {
            page = normalizePage(page);
            rows = normalizeRows(rows);
        }
    }

    private static int normalizePage(int page) { return page < 1 ? 1 : page; }
    private static int normalizeRows(int rows) { return rows < 1 ? 20 : Math.min(rows, 200); }
}
