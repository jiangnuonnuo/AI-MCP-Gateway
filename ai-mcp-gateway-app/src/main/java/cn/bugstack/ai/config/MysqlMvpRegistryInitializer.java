package cn.bugstack.ai.config;

import cn.bugstack.ai.domain.mysql.adapter.port.IMysqlTemplateRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * 应用启动时装配经过评审的 MVP 模板。
 */
@Component
public class MysqlMvpRegistryInitializer {

    @Resource(name = "mysqlTemplateRegistry")
    private IMysqlTemplateRegistry templateRegistry;

    @Resource
    private MysqlConnectionProperties properties;

    /** 仅在 MVP 开关开启时注册应用内置模板。 */
    @PostConstruct
    public void registerMvpTemplate() {
        if (!properties.isMvpEnabled()) {
            return;
        }
        templateRegistry.save(MysqlMvpTemplateFactory.publishedTemplate());
    }
}
