package cn.bugstack.ai.test.infrastructure.adapter.repository;

import cn.bugstack.ai.domain.mysql.model.valobj.MysqlDataSourceRef;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlDataSourceStatus;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplateStatus;
import cn.bugstack.ai.domain.mysql.service.MysqlDataSourceLifecycleService;
import cn.bugstack.ai.domain.mysql.service.MysqlTemplateLifecycleService;
import cn.bugstack.ai.infrastructure.adapter.repository.InMemoryMysqlDataSourceRegistry;
import cn.bugstack.ai.infrastructure.adapter.repository.InMemoryMysqlTemplateRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryMysqlRegistryTest {

    @Test
    void disabledDataSourceCannotBeResolvedAsEnabled() {
        InMemoryMysqlDataSourceRegistry registry = new InMemoryMysqlDataSourceRegistry();
        registry.save(MysqlDataSourceRef.builder()
                .id("warehouse-test")
                .status(MysqlDataSourceStatus.ENABLED)
                .policy(cn.bugstack.ai.domain.mysql.model.valobj.MysqlQueryPolicy.defaults()).build());
        assertTrue(registry.find("warehouse-test").orElseThrow().isEnabled());
        MysqlDataSourceLifecycleService lifecycle = new MysqlDataSourceLifecycleService();
        org.springframework.test.util.ReflectionTestUtils.setField(lifecycle, "dataSourceRegistry", registry);
        lifecycle.disable("warehouse-test");
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
        MysqlTemplateLifecycleService lifecycle = new MysqlTemplateLifecycleService();
        org.springframework.test.util.ReflectionTestUtils.setField(lifecycle, "templateRegistry", registry);
        lifecycle.publish("draft", "1");
        assertTrue(registry.findPublished("draft", "1").isPresent());
        lifecycle.disable("draft", "1");
        assertFalse(registry.findPublished("draft", "1").isPresent());
        registry.delete("draft", "1");
        assertFalse(registry.find("draft", "1").isPresent());
    }
}
