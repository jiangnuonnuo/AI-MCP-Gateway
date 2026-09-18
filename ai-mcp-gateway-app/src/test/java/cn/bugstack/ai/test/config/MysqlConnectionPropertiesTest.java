package cn.bugstack.ai.test.config;

import cn.bugstack.ai.config.MysqlConnectionProperties;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlQueryPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** App Config 只承载运行时技术上限，不再承载业务数据源连接信息。 */
class MysqlConnectionPropertiesTest {

    @Test
    void exposesOnlyTechnicalPolicy() {
        MysqlConnectionProperties properties = MysqlConnectionProperties.builder().maxRows(25).maxColumns(8).build();
        MysqlQueryPolicy policy = properties.technicalPolicy();
        assertEquals(25, policy.getMaxRows());
        assertEquals(8, policy.getMaxColumns());
    }

    @Test
    void rejectsNonPositiveRuntimeLimits() {
        MysqlConnectionProperties properties = MysqlConnectionProperties.builder().maxRows(0).build();
        assertThrows(IllegalArgumentException.class, properties::technicalPolicy);
    }
}
