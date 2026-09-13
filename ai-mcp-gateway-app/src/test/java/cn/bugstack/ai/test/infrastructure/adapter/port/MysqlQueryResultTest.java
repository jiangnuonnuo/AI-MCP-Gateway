package cn.bugstack.ai.test.infrastructure.adapter.port;

import cn.bugstack.ai.domain.mysql.model.valobj.MysqlQueryResult;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MysqlQueryResultTest {

    @Test
    void preservesNullValuesInStructuredRows() {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("optional_value", null);
        MysqlQueryResult result = new MysqlQueryResult("q", List.of(
                new MysqlQueryResult.MysqlColumn("optional_value", "optional_value", "VARCHAR")),
                List.of(row), false, 4);
        assertNull(result.getRows().get(0).get("optional_value"));
    }

    @Test
    void exposesLogicalTypesForEmptyAndTruncatedResults() {
        MysqlQueryResult result = new MysqlQueryResult("q-2", List.of(
                new MysqlQueryResult.MysqlColumn("amount", "amount", "DECIMAL"),
                new MysqlQueryResult.MysqlColumn("created_at", "created_at", "DATETIME")),
                List.of(), true, 0);

        var structured = result.toStructuredMap();
        assertEquals("DECIMAL", ((java.util.Map<?, ?>) ((List<?>) structured.get("columns")).get(0)).get("type"));
        assertEquals(0, structured.get("rowCount"));
        assertTrue((Boolean) structured.get("truncated"));
        assertEquals(List.of(), structured.get("rows"));
    }
}
