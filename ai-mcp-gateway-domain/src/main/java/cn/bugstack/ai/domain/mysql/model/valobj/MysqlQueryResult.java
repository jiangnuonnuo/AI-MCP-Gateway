package cn.bugstack.ai.domain.mysql.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MySQL 查询的结构化、边界内结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MysqlQueryResult {

    /** 查询关联标识。 */
    private String queryId;

    /** 返回列的名称、标签和对外逻辑类型。 */
    private List<MysqlColumn> columns;

    /** 返回行数据，行内保留 NULL 值。 */
    private List<Map<String, Object>> rows;

    /** 结果是否因资源上限被截断。 */
    private boolean truncated;

    /** 结果估算字节数。 */
    private long resultBytes;

    public int getRowCount() {
        return rows == null ? 0 : rows.size();
    }

    /**
     * 将 JDBC 行结果转换为 MCP 结构化内容。
     */
    public Map<String, Object> toStructuredMap() {
        List<Map<String, Object>> columnMaps = columns == null
                ? List.of()
                : columns.stream().map(MysqlColumn::toMap).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("columns", columnMaps);
        result.put("rows", rows == null ? List.of() : rows);
        result.put("rowCount", getRowCount());
        result.put("truncated", truncated);
        result.put("queryId", queryId);
        return result;
    }

    /**
     * 对外返回列的结构化描述。类型使用稳定的逻辑名称，不暴露 JDBC 类型编号。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MysqlColumn {

        /** 数据库原始列名。 */
        private String name;

        /** 查询结果列标签。 */
        private String label;

        /** 逻辑类型名称，例如 INT、DECIMAL、DATETIME 或 VARCHAR。 */
        private String type;

        public Map<String, Object> toMap() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("name", name);
            value.put("label", label);
            value.put("type", type);
            return value;
        }
    }

    /**
     * 将结果行转换为只读快照，供查询端口在返回前调用。
     */
    public void normalize() {
        columns = List.copyOf(columns == null ? List.of() : columns);
        List<Map<String, Object>> copy = new ArrayList<>();
        if (rows != null) {
            rows.forEach(row -> copy.add(Collections.unmodifiableMap(new LinkedHashMap<>(row))));
        }
        rows = List.copyOf(copy);
    }
}
