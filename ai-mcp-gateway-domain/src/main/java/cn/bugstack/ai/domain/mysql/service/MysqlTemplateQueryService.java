package cn.bugstack.ai.domain.mysql.service;

import cn.bugstack.ai.domain.mysql.adapter.port.IMysqlDataSourceRegistry;
import cn.bugstack.ai.domain.mysql.adapter.port.IMysqlQueryPort;
import cn.bugstack.ai.domain.mysql.adapter.port.IMysqlProtocolRepository;
import cn.bugstack.ai.domain.mysql.adapter.port.ISqlSafetyPort;
import cn.bugstack.ai.domain.mysql.model.command.MysqlQueryCommand;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlDataSourceRef;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlExecutionStage;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlExecutionTrace;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlParameterType;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlQueryPolicy;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlQueryResult;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplate;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplateParameter;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplateTestReport;
import cn.bugstack.ai.domain.mysql.model.valobj.SqlSafetyDecision;
import cn.bugstack.ai.types.exception.MysqlDomainException;
import cn.bugstack.ai.types.exception.MysqlQueryException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.temporal.TemporalAccessor;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

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
     * 执行管理端模板测试并返回阶段化报告。参数、SQL、数据源和策略均从已发布模板解析，
     * 管理端请求只提供模板参数，不能改变执行边界。
     */
    public MysqlTemplateTestReport executeWithReport(String templateRef, String version,
                                                      Map<String, ?> arguments, String queryId) {
        long startedAt = System.nanoTime();
        String effectiveQueryId = queryId == null || queryId.isBlank() ? UUID.randomUUID().toString() : queryId;
        Map<String, Object> requestParameters = snapshotParameters(arguments);
        MysqlExecutionTrace trace = new MysqlExecutionTrace();
        MysqlTemplate template = null;
        try {
            template = requireEnabledTemplate(templateRef, version);
            MysqlQueryResult result = execute(template, arguments, null, effectiveQueryId, trace);
            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("rowCount", result.getRowCount());
            metrics.put("columnCount", result.getColumns() == null ? 0 : result.getColumns().size());
            metrics.put("truncated", result.isTruncated());
            metrics.put("resultBytes", result.getResultBytes());
            return MysqlTemplateTestReport.builder()
                    .success(true)
                    .templateRef(template.getId())
                    .version(template.getVersion())
                    .datasourceRef(template.getDatasourceRef())
                    .queryId(result.getQueryId())
                    .requestParameters(requestParameters)
                    .stages(trace.snapshot())
                    .durationMs(elapsedMs(startedAt))
                    .metrics(metrics)
                    .responseJson(result.toStructuredMap())
                    .build();
        } catch (MysqlDomainException exception) {
            markFailure(trace, exception.getCode(), safeMessage(exception), exceptionStage(trace));
            return failedReport(template, templateRef, version, effectiveQueryId, requestParameters, trace,
                    elapsedMs(startedAt), exception.getCode(), safeMessage(exception));
        } catch (MysqlQueryException exception) {
            markFailure(trace, exception.getCode(), "MySQL query failed", exceptionStage(trace));
            return failedReport(template, templateRef, version, effectiveQueryId, requestParameters, trace,
                    elapsedMs(startedAt), exception.getCode(), "MySQL query failed");
        } catch (RuntimeException exception) {
            markFailure(trace, "MYSQL_EXECUTION_ERROR", "MySQL query failed", exceptionStage(trace));
            return failedReport(template, templateRef, version, effectiveQueryId, requestParameters, trace,
                    elapsedMs(startedAt), "MYSQL_EXECUTION_ERROR", "MySQL query failed");
        }
    }

    /**
     * 执行已经由 Gateway 绑定解析出的协议记录。调用方不能通过参数覆盖 SQL、数据源或策略。
     */
    public MysqlQueryResult execute(MysqlTemplate template, Map<String, ?> arguments,
                                    MysqlQueryPolicy requestedPolicy, String queryId) {
        return execute(template, arguments, requestedPolicy, queryId, null);
    }

    /** 执行外部传入的动态只读 SQL；数据源和资源策略仍由已绑定 Tool 提供。 */
    public MysqlQueryResult executeDynamic(String sql, String datasourceRef, Map<String, ?> arguments,
                                           MysqlQueryPolicy requestedPolicy, String queryId) {
        if (sql == null || sql.isBlank() || datasourceRef == null || datasourceRef.isBlank()) {
            throw new MysqlDomainException("INVALID_ARGUMENT", "dynamic SQL request is invalid");
        }
        Map<String, Object> parameters = normalizeArguments(arguments);
        validateDynamicParameters(parameters);
        MysqlDataSourceRef dataSource = requireEnabledDataSource(datasourceRef);
        MysqlQueryPolicy sourcePolicy = dataSource.getPolicy() == null
                ? MysqlQueryPolicy.defaults() : dataSource.getPolicy();
        MysqlQueryPolicy effectivePolicy = requestedPolicy == null
                ? sourcePolicy : requestedPolicy.boundedBy(sourcePolicy);
        effectivePolicy.validate();
        SqlSafetyDecision decision = safetyPort.validateDynamic(sql, parameters, effectivePolicy);
        if (decision == null || !decision.isAllowed()) {
            throw new MysqlDomainException(decision == null ? "SQL_POLICY_REJECTED" : decision.getCode(),
                    decision == null ? "SQL policy rejected" : decision.getReason());
        }
        MysqlQueryCommand command = MysqlQueryCommand.builder()
                .sql(sql)
                .datasourceRef(datasourceRef)
                .parameters(parameters)
                .requestedPolicy(effectivePolicy)
                .queryId(queryId)
                .build();
        command.normalize();
        return queryPort.execute(command);
    }

    private MysqlQueryResult execute(MysqlTemplate template, Map<String, ?> arguments,
                                     MysqlQueryPolicy requestedPolicy, String queryId,
                                     MysqlExecutionTrace trace) {
        if (template == null) {
            throw new MysqlDomainException("PROTOCOL_UNAVAILABLE", "MySQL protocol is unavailable");
        }
        if (!template.isEnabled()) {
            throw new MysqlDomainException("PROTOCOL_UNAVAILABLE", "MySQL protocol is unavailable");
        }
        if (trace != null) trace.start("PARAMETER_VALIDATION");
        Map<String, Object> parameters = normalizeArguments(arguments);
        validateTemplateParameters(template, parameters);
        if (trace != null) trace.succeed("PARAMETER_VALIDATION");
        if (trace != null) trace.start("POLICY_VALIDATION");
        MysqlDataSourceRef dataSource = requireEnabledDataSource(template.getDatasourceRef());
        MysqlQueryPolicy effectivePolicy = effectivePolicy(template, dataSource, requestedPolicy);
        SqlSafetyDecision decision = safetyPort.validate(template.getSql(), parameters, effectivePolicy);
        if (decision == null || !decision.isAllowed()) {
            throw new MysqlDomainException(decision == null ? "SQL_POLICY_REJECTED" : decision.getCode(),
                    decision == null ? "SQL policy rejected" : decision.getReason());
        }
        if (trace != null) trace.succeed("POLICY_VALIDATION");

        MysqlQueryCommand command = MysqlQueryCommand.builder()
                .template(template)
                .parameters(parameters)
                .requestedPolicy(effectivePolicy)
                .queryId(queryId)
                .build();
        command.normalize();
        return trace == null ? queryPort.execute(command) : queryPort.executeWithTrace(command, trace);
    }

    private static void markFailure(MysqlExecutionTrace trace, String code, String message, String stage) {
        if (stage != null) {
            MysqlExecutionStage current = trace.snapshot().stream()
                    .filter(value -> stage.equals(value.getName())).findFirst().orElse(null);
            if (current != null && current.getStatus() != MysqlExecutionStage.Status.FAILED
                    && current.getStatus() != MysqlExecutionStage.Status.SUCCEEDED) {
                trace.fail(stage, code, message);
            }
        }
    }

    private static String exceptionStage(MysqlExecutionTrace trace) {
        String running = trace.currentStage();
        if (running != null) return running;
        return trace.snapshot().stream().filter(value -> value.getStatus() == MysqlExecutionStage.Status.FAILED)
                .map(MysqlExecutionStage::getName).findFirst().orElse("PARAMETER_VALIDATION");
    }

    private static MysqlTemplateTestReport failedReport(MysqlTemplate template, String templateRef, String version,
                                                         String queryId, Map<String, Object> requestParameters,
                                                         MysqlExecutionTrace trace, long durationMs, String code,
                                                         String message) {
        String resolvedRef = template == null ? templateRef : template.getId();
        String resolvedVersion = template == null ? version : template.getVersion();
        String datasourceRef = template == null ? null : template.getDatasourceRef();
        String failedStage = trace.snapshot().stream()
                .filter(value -> value.getStatus() == MysqlExecutionStage.Status.FAILED)
                .map(MysqlExecutionStage::getName).findFirst().orElse(null);
        return MysqlTemplateTestReport.builder().success(false).templateRef(resolvedRef).version(resolvedVersion)
                .datasourceRef(datasourceRef).queryId(queryId).requestParameters(requestParameters)
                .stages(trace.snapshot()).durationMs(durationMs).metrics(Map.of()).responseJson(Map.of())
                .errorCode(code).errorMessage(message).failedStage(failedStage).build();
    }

    private static String safeMessage(MysqlDomainException exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? "MySQL template test failed" : exception.getMessage();
    }

    private static long elapsedMs(long startedAt) {
        return Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
    }

    private static Map<String, Object> snapshotParameters(Map<String, ?> arguments) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        if (arguments == null) return snapshot;
        arguments.forEach((key, value) -> snapshot.put(key, isSensitive(key) ? "***" : value));
        return snapshot;
    }

    private static boolean isSensitive(String key) {
        if (key == null) return false;
        String normalized = key.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        return normalized.contains("password") || normalized.contains("secret") || normalized.contains("token")
                || normalized.contains("authorization") || normalized.contains("credential") || normalized.contains("jdbcurl")
                || normalized.contains("privatekey") || normalized.endsWith("apikey");
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

    private static void validateDynamicParameters(Map<String, Object> parameters) {
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || !isScalar(entry.getValue())) {
                throw new MysqlDomainException("SQL_PARAMETER_ERROR", "dynamic SQL parameter is invalid");
            }
        }
    }

    private static boolean isScalar(Object value) {
        return value == null || value instanceof CharSequence || value instanceof Number || value instanceof Boolean;
    }
}
