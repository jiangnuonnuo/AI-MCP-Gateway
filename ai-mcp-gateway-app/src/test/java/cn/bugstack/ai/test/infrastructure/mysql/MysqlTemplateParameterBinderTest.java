package cn.bugstack.ai.test.infrastructure.mysql;

import cn.bugstack.ai.domain.mysql.model.valobj.MysqlParameterType;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplate;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplateParameter;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplateStatus;
import cn.bugstack.ai.infrastructure.mysql.MysqlTemplateParameterBinder;
import cn.bugstack.ai.types.exception.MysqlParameterException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MysqlTemplateParameterBinderTest {

    private final MysqlTemplateParameterBinder binder = new MysqlTemplateParameterBinder();
    private final MysqlTemplate template = new MysqlTemplate("t", "1", "t", "", "warehouse-test",
            "SELECT * FROM fact_order WHERE order_time >= :fromTime AND channel_id = :channelId",
            List.of(new MysqlTemplateParameter("fromTime", MysqlParameterType.DATETIME, true),
                    new MysqlTemplateParameter("channelId", MysqlParameterType.INTEGER, true)),
            MysqlTemplateStatus.ENABLED, null);

    @Test
    void bindsNamedParametersWithoutStringConcatenation() {
        MysqlTemplateParameterBinder.BoundSql bound = binder.bind(template,
                Map.of("fromTime", "2024-01-01 00:00:00", "channelId", 1));
        assertEquals("SELECT * FROM fact_order WHERE order_time >= ? AND channel_id = ?", bound.sql());
        assertEquals(List.of("2024-01-01 00:00:00", 1), bound.values());
    }

    @Test
    void rejectsUndeclaredParametersWhileLeavingBusinessValidationToDomain() {
        assertThrows(MysqlParameterException.class,
                () -> binder.bind(template, Map.of("fromTime", "2024-01-01 00:00:00", "channelId", 1,
                        "unexpected", true)));
    }
}
