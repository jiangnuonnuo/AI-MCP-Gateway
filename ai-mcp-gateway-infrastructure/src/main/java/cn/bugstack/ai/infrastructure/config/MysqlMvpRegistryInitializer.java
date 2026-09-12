package cn.bugstack.ai.infrastructure.config;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import cn.bugstack.ai.infrastructure.adapter.port.InMemoryMysqlTemplateRegistry;

/**
 * 将经过代码评审的 MVP 模板注册到运行时 Registry；数据源凭证仍由运行时环境单独注入。
 */
@Component
public class MysqlMvpRegistryInitializer {

    private final InMemoryMysqlTemplateRegistry templateRegistry;

    public MysqlMvpRegistryInitializer(InMemoryMysqlTemplateRegistry templateRegistry) {
        this.templateRegistry = templateRegistry;
    }

    @PostConstruct
    public void registerMvpTemplate() {
        templateRegistry.register(MysqlMvpTemplateFactory.publishedTemplate());
    }
}
