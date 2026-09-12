package cn.bugstack.ai.infrastructure.adapter.port;

import cn.bugstack.ai.domain.mysql.model.valobj.MysqlQueryResult;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;

class MysqlQueryResultTest {

    @Test
    void preservesNullValuesInStructuredRows() {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("optional_value", null);
        MysqlQueryResult result = new MysqlQueryResult("q", List.of(
                new MysqlQueryResult.MysqlColumn("optional_value", "optional_value", 12, "VARCHAR")),
                List.of(row), false, 4);
        assertNull(result.getRows().get(0).get("optional_value"));
    }
}
