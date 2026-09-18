package cn.bugstack.ai.domain.mysql.service;

import cn.bugstack.ai.domain.mysql.adapter.port.IMysqlDataSourceRegistry;
import cn.bugstack.ai.domain.mysql.adapter.port.IMysqlQueryPort;
import cn.bugstack.ai.domain.mysql.adapter.port.IMysqlProtocolRepository;
import cn.bugstack.ai.domain.mysql.adapter.port.ISqlSafetyPort;
import cn.bugstack.ai.domain.mysql.model.command.MysqlQueryCommand;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlDataSourceRef;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlParameterType;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlQueryPolicy;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlQueryResult;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplate;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplateParameter;
import cn.bugstack.ai.domain.mysql.model.valobj.SqlSafetyDecision;
import cn.bugstack.ai.types.exception.MysqlDomainException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.temporal.TemporalAccessor;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MySQL 模板查询领域服务，负责业务流程和规则编排，不直接依赖 JDBC。
 */
@Service("mysqlTemplateQueryService")
public class MysqlTemplateQueryService {

    @Resource(name = "mysqlProtocolRepository")
    private IMysqlProtocolRepository protocolRepository;

    @Resource(name = "mysqlDataSourceRegistry")
    private IMysqlDataSourceRegistry dataSourceRegistry;

    @Resource
    private ISqlSafetyPort safetyPort;

    @Resource(name = "mysqlQueryPort")
    private IMysqlQueryPort queryPort;

    /**
     * 执行一个已发布的 MySQL 模板查询。
     *
     * @param templateRef 模板引用
     * @param version 模板版本
     * @param arguments 调用参数
     * @param requestedPolicy 调用方请求的收紧策略
     * @param queryId 查询关联标识
     * @return 查询结果
     */
    public MysqlQueryResult execute(String templateRef, String version, Map<String, ?> arguments,
                                    MysqlQueryPolicy requestedPolicy, String queryId) {
        MysqlTemplate template = requireEnabledTemplate(templateRef, version);
        return execute(template, arguments, requestedPolicy, queryId);
    }

    /**
     * 执行已经由 Gateway 绑定解析出的协议记录。调用方不能通过参数覆盖 SQL、数据源或策略。
     */
    public MysqlQueryResult execute(MysqlTemplate template, Map<String, ?> arguments,
                                    MysqlQueryPolicy requestedPolicy, String queryId) {
        if (template == null) {
            throw new MysqlDomainException("PROTOCOL_UNAVAILABLE", "MySQL protocol is unavailable");
        }
        if (!template.isEnabled()) {
            throw new MysqlDomainException("PROTOCOL_UNAVAILABLE", "MySQL protocol is unavailable");
        }
        MysqlDataSourceRef dataSource = requireEnabledDataSource(template.getDatasourceRef());
        Map<String, Object> parameters = normalizeArguments(arguments);

        validateTemplateParameters(template, parameters);
        MysqlQueryPolicy effectivePolicy = effectivePolicy(template, dataSource, requestedPolicy);
        SqlSafetyDecision decision = safetyPort.validate(template.getSql(), parameters, effectivePolicy);
        if (decision == null || !decision.isAllowed()) {
            throw new MysqlDomainException(decision == null ? "SQL_POLICY_REJECTED" : decision.getCode(),
                    decision == null ? "SQL policy rejected" : decision.getReason());
        }

        MysqlQueryCommand command = MysqlQueryCommand.builder()
                .template(template)
                .parameters(parameters)
                .requestedPolicy(effectivePolicy)
                .queryId(queryId)
                .build();
        command.normalize();
        return queryPort.execute(command);
    }

    private MysqlTemplate requireEnabledTemplate(String templateRef, String version) {
        if (protocolRepository == null) {
            throw new MysqlDomainException("PROTOCOL_UNAVAILABLE", "MySQL protocol is unavailable");
        }
        MysqlTemplate template = protocolRepository.find(templateRef, version)
                .filter(MysqlTemplate::isEnabled).orElse(null);
        if (template == null) {
            throw new MysqlDomainException("PROTOCOL_UNAVAILABLE", "MySQL protocol is unavailable");
        }
        try {
            template.validate();
        } catch (IllegalArgumentException e) {
            throw new MysqlDomainException("SQL_PARAMETER_ERROR", "template configuration is invalid");
        }
        return template;
    }

    private MysqlDataSourceRef requireEnabledDataSource(String datasourceRef) {
        if (dataSourceRegistry == null) {
            throw new MysqlDomainException("DATASOURCE_UNAVAILABLE", "data source registry is unavailable");
        }
        MysqlDataSourceRef dataSource = dataSourceRegistry.find(datasourceRef).orElse(null);
        if (dataSource == null || !dataSource.isEnabled()) {
            throw new MysqlDomainException("DATASOURCE_UNAVAILABLE", "data source is unavailable");
        }
        try {
            dataSource.validate();
        } catch (IllegalArgumentException e) {
            throw new MysqlDomainException("DATASOURCE_UNAVAILABLE", "data source configuration is invalid");
        }
        return dataSource;
    }

    private MysqlQueryPolicy effectivePolicy(MysqlTemplate template, MysqlDataSourceRef dataSource,
                                             MysqlQueryPolicy requestedPolicy) {
        MysqlQueryPolicy templatePolicy = template.getPolicy() == null
                ? MysqlQueryPolicy.defaults() : template.getPolicy();
        MysqlQueryPolicy dataSourcePolicy = dataSource.getPolicy() == null
                ? MysqlQueryPolicy.defaults() : dataSource.getPolicy();
        MysqlQueryPolicy effective = templatePolicy.boundedBy(dataSourcePolicy);
        if (requestedPolicy != null) {
            effective = requestedPolicy.boundedBy(effective);
        }
        effective.validate();
        return effective;
    }

    private void validateTemplateParameters(MysqlTemplate template, Map<String, Object> arguments) {
        Map<String, MysqlTemplateParameter> definitions = new HashMap<>();
        for (MysqlTemplateParameter parameter : template.getParameters()) {
            parameter.validate();
            if (definitions.put(parameter.getName(), parameter) != null) {
                throw new MysqlDomainException("SQL_PARAMETER_ERROR", "duplicate template definition");
            }
            if (parameter.isRequired() && !arguments.containsKey(parameter.getName())) {
                throw new MysqlDomainException("SQL_PARAMETER_ERROR", "missing template parameter");
            }
        }
        for (Map.Entry<String, Object> entry : arguments.entrySet()) {
            MysqlTemplateParameter definition = definitions.get(entry.getKey());
            if (definition == null || !isType(entry.getValue(), definition.getType())) {
                throw new MysqlDomainException("SQL_PARAMETER_ERROR", "template parameter is invalid");
            }
        }
    }

    private static boolean isType(Object value, MysqlParameterType type) {
        if (value == null) return true;
        return switch (type) {
            case STRING -> value instanceof CharSequence;
            case INTEGER -> value instanceof Byte || value instanceof Short || value instanceof Integer;
            case LONG -> value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long;
            case DECIMAL -> value instanceof BigDecimal || value instanceof Byte || value instanceof Short
                    || value instanceof Integer || value instanceof Long || value instanceof Float || value instanceof Double;
            case BOOLEAN -> value instanceof Boolean;
            case DATE, TIME, DATETIME, TIMESTAMP -> value instanceof CharSequence
                    || value instanceof java.util.Date || value instanceof TemporalAccessor;
        };
    }

    private static Map<String, Object> normalizeArguments(Map<String, ?> arguments) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        if (arguments != null) {
            arguments.forEach(normalized::put);
        }
        return normalized;
    }
}
