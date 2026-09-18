package cn.bugstack.ai.domain.mysql.service;

import cn.bugstack.ai.domain.mysql.adapter.port.IMysqlProtocolRepository;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplate;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplateStatus;
import cn.bugstack.ai.types.exception.MysqlDomainException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/** MySQL 协议生命周期领域服务，统一使用 ENABLED/DISABLED 两态。 */
@Service
public class MysqlTemplateLifecycleService {

    @Resource(name = "mysqlProtocolRepository")
    private IMysqlProtocolRepository protocolRepository;

    /** 启用已经完成本地校验的协议记录。 */
    public void enable(String templateRef, String version) {
        transition(templateRef, version, MysqlTemplateStatus.ENABLED);
    }

    /** 停用协议记录；停用不会删除其管理数据。 */
    public void disable(String templateRef, String version) {
        transition(templateRef, version, MysqlTemplateStatus.DISABLED);
    }

    private void transition(String templateRef, String version, MysqlTemplateStatus target) {
        MysqlTemplate template = protocolRepository.find(templateRef, version)
                .orElseThrow(() -> new MysqlDomainException("TEMPLATE_NOT_FOUND", "template is not found"));
        MysqlTemplateStatus current = template.getStatus();
        boolean allowed = (target == MysqlTemplateStatus.ENABLED || target == MysqlTemplateStatus.DISABLED)
                && current != null;
        if (!allowed) {
            throw new MysqlDomainException("TEMPLATE_STATUS_INVALID", "template status transition is invalid");
        }
        template.setStatus(target);
        protocolRepository.save(template);
    }
}
