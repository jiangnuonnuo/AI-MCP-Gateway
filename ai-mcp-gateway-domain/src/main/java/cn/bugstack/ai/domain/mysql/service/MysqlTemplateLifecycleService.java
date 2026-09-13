package cn.bugstack.ai.domain.mysql.service;

import cn.bugstack.ai.domain.mysql.adapter.port.IMysqlTemplateRegistry;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplate;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplateStatus;
import cn.bugstack.ai.types.exception.MysqlDomainException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * MySQL 模板生命周期领域服务，集中管理发布、停用和废弃规则。
 */
@Service
public class MysqlTemplateLifecycleService {

    @Resource(name = "mysqlTemplateRegistry")
    private IMysqlTemplateRegistry templateRegistry;

    /** 发布草稿模板。 */
    public void publish(String templateRef, String version) {
        transition(templateRef, version, MysqlTemplateStatus.PUBLISHED);
    }

    /** 停用已发布模板。 */
    public void disable(String templateRef, String version) {
        transition(templateRef, version, MysqlTemplateStatus.DISABLED);
    }

    /** 废弃已发布或已停用模板。 */
    public void deprecate(String templateRef, String version) {
        transition(templateRef, version, MysqlTemplateStatus.DEPRECATED);
    }

    private void transition(String templateRef, String version, MysqlTemplateStatus target) {
        MysqlTemplate template = templateRegistry.find(templateRef, version)
                .orElseThrow(() -> new MysqlDomainException("TEMPLATE_NOT_FOUND", "template is not found"));
        MysqlTemplateStatus current = template.getStatus();
        boolean allowed = switch (target) {
            case PUBLISHED -> current == MysqlTemplateStatus.DRAFT;
            case DISABLED -> current == MysqlTemplateStatus.PUBLISHED;
            case DEPRECATED -> current == MysqlTemplateStatus.PUBLISHED || current == MysqlTemplateStatus.DISABLED;
            case DRAFT -> false;
        };
        if (!allowed) {
            throw new MysqlDomainException("TEMPLATE_STATUS_INVALID", "template status transition is invalid");
        }
        template.setStatus(target);
        templateRegistry.save(template);
    }
}
