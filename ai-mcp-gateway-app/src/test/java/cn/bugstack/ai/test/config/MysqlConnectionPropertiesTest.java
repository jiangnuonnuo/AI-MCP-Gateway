package cn.bugstack.ai.test.config;

import cn.bugstack.ai.config.MysqlConnectionProperties;
import cn.bugstack.ai.config.MysqlDataSourceConfig;
import cn.bugstack.ai.config.MysqlMvpDataSourceInitializer;
import cn.bugstack.ai.config.MysqlMvpRegistryInitializer;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlDataSourceStatus;
import cn.bugstack.ai.infrastructure.adapter.repository.InMemoryMysqlDataSourceRegistry;
import cn.bugstack.ai.infrastructure.adapter.repository.InMemoryMysqlTemplateRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证启动配置与领域数据源引用之间的组合根转换。 */
class MysqlConnectionPropertiesTest {

    @Test
    void findsConfiguredTechnicalSettingsWithoutLeakingIntoDomainReference() {
        MysqlDataSourceConfig config = MysqlDataSourceConfig.builder()
                .jdbcUrl("jdbc:mysql://localhost/test")
                .username("warehouse")
                .passwordSecretRef("env:WAREHOUSE_MYSQL_PASSWORD")
                .build();
        MysqlConnectionProperties properties = MysqlConnectionProperties.builder()
                .datasource(Map.of("warehouse-test", config))
                .build();

        assertTrue(properties.find("warehouse-test").isPresent());
        assertEquals("warehouse-test", properties.find("warehouse-test").orElseThrow().getId());
        assertFalse(properties.find("missing").isPresent());
    }

    @Test
    void skipsMissingCredentialsAndKeepsDisabledSourceDisabled() {
        MysqlConnectionProperties properties = MysqlConnectionProperties.builder()
                .datasource(Map.of(
                        "missing-user", MysqlDataSourceConfig.builder()
                                .jdbcUrl("jdbc:mysql://localhost/test").build(),
                        "disabled", MysqlDataSourceConfig.builder()
                                .jdbcUrl("jdbc:mysql://localhost/test")
                                .username("warehouse").enabled(false).build()))
                .build();
        InMemoryMysqlDataSourceRegistry registry = new InMemoryMysqlDataSourceRegistry();
        MysqlMvpDataSourceInitializer initializer = new MysqlMvpDataSourceInitializer();
        ReflectionTestUtils.setField(initializer, "properties", properties);
        ReflectionTestUtils.setField(initializer, "registry", registry);

        initializer.registerRuntimeDataSource();

        assertFalse(registry.find("missing-user").isPresent());
        assertEquals(MysqlDataSourceStatus.DISABLED,
                registry.find("disabled").orElseThrow().getStatus());
    }

    @Test
    void doesNotRegisterMvpTemplateWhenFeatureIsDisabled() {
        MysqlMvpRegistryInitializer initializer = new MysqlMvpRegistryInitializer();
        InMemoryMysqlTemplateRegistry registry = new InMemoryMysqlTemplateRegistry();
        ReflectionTestUtils.setField(initializer, "properties",
                MysqlConnectionProperties.builder().mvpEnabled(false).build());
        ReflectionTestUtils.setField(initializer, "templateRegistry", registry);

        initializer.registerMvpTemplate();

        assertFalse(registry.findPublished("warehouse_channel_sales", "1").isPresent());
    }
}
