package cn.bugstack.ai.domain.mysql.service;

import cn.bugstack.ai.domain.mysql.adapter.port.IMysqlProtocolRepository;
import cn.bugstack.ai.domain.mysql.adapter.port.ISqlSafetyPort;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlQueryPolicy;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplate;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplateParameter;
import cn.bugstack.ai.domain.mysql.model.valobj.SqlSafetyDecision;
import cn.bugstack.ai.types.exception.MysqlDomainException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/** MySQL 协议写入编排：先做本地安全校验，再交给持久化端口。 */
@Service
public class MysqlProtocolManagementService {

    @Resource(name = "mysqlProtocolRepository")
    private IMysqlProtocolRepository protocolRepository;

    @Resource
    private ISqlSafetyPort safetyPort;

    /** 保存已完成字段、参数契约和 SQL 责任链校验的协议。 */
    public void save(MysqlTemplate protocol) {
        if (protocol == null) throw new MysqlDomainException("PROTOCOL_INVALID", "MySQL protocol is invalid");
        try {
            protocol.validate();
            MysqlQueryPolicy policy = protocol.getPolicy() == null
                    ? MysqlQueryPolicy.defaults() : protocol.getPolicy();
            Map<String, Object> parameters = new LinkedHashMap<>();
            for (MysqlTemplateParameter parameter : protocol.getParameters()) {
                parameter.validate();
                if (parameters.containsKey(parameter.getName())) {
                    throw new MysqlDomainException("SQL_PARAMETER_ERROR", "duplicate protocol parameter");
                }
                parameters.put(parameter.getName(), null);
            }
            SqlSafetyDecision decision = safetyPort.validate(protocol.getSql(), parameters, policy);
            if (decision == null || !decision.isAllowed()) {
                throw new MysqlDomainException(decision == null ? "SQL_POLICY_REJECTED" : decision.getCode(),
                        decision == null ? "SQL policy rejected" : decision.getReason());
            }
            protocolRepository.save(protocol);
        } catch (MysqlDomainException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new MysqlDomainException("PROTOCOL_INVALID", "MySQL protocol is invalid");
        }
    }
}
