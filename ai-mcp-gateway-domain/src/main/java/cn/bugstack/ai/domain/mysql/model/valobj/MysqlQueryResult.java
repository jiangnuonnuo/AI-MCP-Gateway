package cn.bugstack.ai.domain.mysql.model.valobj;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;

/** JDBC 查询的结构化、边界内结果。 */
public final class MysqlQueryResult {
    private final String queryId;
    private final List<MysqlColumn> columns;
    private final List<Map<String, Object>> rows;
    private final boolean truncated;
    private final long resultBytes;

    public MysqlQueryResult(String queryId, List<MysqlColumn> columns, List<Map<String, Object>> rows,
                            boolean truncated, long resultBytes) {
        this.queryId = queryId;
        this.columns = List.copyOf(columns == null ? List.of() : columns);
        List<Map<String, Object>> copy = new ArrayList<>();
        if (rows != null) rows.forEach(row -> copy.add(Collections.unmodifiableMap(new LinkedHashMap<>(row))));
        this.rows = List.copyOf(copy);
        this.truncated = truncated;
        this.resultBytes = resultBytes;
    }

    public String getQueryId() { return queryId; }
    public List<MysqlColumn> getColumns() { return columns; }
    public List<Map<String, Object>> getRows() { return rows; }
    public int getRowCount() { return rows.size(); }
    public boolean isTruncated() { return truncated; }
    public long getResultBytes() { return resultBytes; }

    public Map<String, Object> toStructuredMap() {
        List<Map<String, Object>> columnMaps = columns.stream().map(MysqlColumn::toMap).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("columns", columnMaps);
        result.put("rows", rows);
        result.put("rowCount", getRowCount());
        result.put("truncated", truncated);
        result.put("queryId", queryId);
        return result;
    }

    public record MysqlColumn(String name, String label, int sqlType, String typeName) {
        public Map<String, Object> toMap() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("name", name);
            value.put("label", label);
            value.put("sqlType", sqlType);
            value.put("typeName", typeName);
            return value;
        }
    }
}
