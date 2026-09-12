package cn.bugstack.ai.infrastructure.adapter.port;

import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplateStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryMysqlRegistryTest {

    @Test
    void disabledDataSourceCannotBeResolvedAsEnabled() {
        InMemoryMysqlDataSourceRegistry registry = new InMemoryMysqlDataSourceRegistry();
        registry.register(cn.bugstack.ai.domain.mysql.model.valobj.MysqlDataSourceConfig.builder()
                .id("warehouse-test").jdbcUrl("jdbc:mysql://127.0.0.1:3306/data_warehouse")
                .username("runtime-user").runtimePassword("runtime-only").enabled(true).build());
        assertTrue(registry.find("warehouse-test").orElseThrow().isEnabled());
        registry.disable("warehouse-test");
        assertFalse(registry.find("warehouse-test").orElseThrow().isEnabled());
    }

    @Test
    void onlyPublishedTemplatesAreListedAndResolved() {
        InMemoryMysqlTemplateRegistry registry = new InMemoryMysqlTemplateRegistry();
        var draft = new cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplate(
                "draft", "1", "draft", "", "warehouse-test", "SELECT 1", java.util.List.of(),
                MysqlTemplateStatus.DRAFT, null);
        registry.register(draft);
        assertTrue(registry.find("draft", "1").isPresent());
        assertFalse(registry.findPublished("draft", "1").isPresent());
        registry.publish("draft", "1");
        assertTrue(registry.findPublished("draft", "1").isPresent());
        registry.disable("draft", "1");
        assertFalse(registry.findPublished("draft", "1").isPresent());
    }
}
