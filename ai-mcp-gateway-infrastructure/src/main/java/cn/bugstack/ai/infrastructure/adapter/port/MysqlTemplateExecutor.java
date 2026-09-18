package cn.bugstack.ai.infrastructure.adapter.port;

import cn.bugstack.ai.domain.mysql.service.MysqlTemplateQueryService;
import cn.bugstack.ai.types.exception.MysqlDomainException;
import cn.bugstack.ai.infrastructure.observability.IMysqlExecutionMetricsPort;
import cn.bugstack.ai.types.exception.MysqlQueryException;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlQueryResult;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplate;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplateStatus;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplateParameter;
import cn.bugstack.ai.domain.session.model.valobj.gateway.McpToolProtocolConfigVO;
import cn.bugstack.ai.domain.tool.executor.ToolExecutor;
import cn.bugstack.ai.domain.tool.model.valobj.ToolBackendType;
import cn.bugstack.ai.domain.tool.model.valobj.ToolExecutionContext;
import cn.bugstack.ai.domain.tool.model.valobj.ToolExecutionErrorCode;
import cn.bugstack.ai.domain.tool.model.valobj.ToolExecutionMode;
import cn.bugstack.ai.domain.tool.model.valobj.ToolExecutionResult;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** MySQL 只读协议的执行策略。协议和数据源均由服务端配置解析，客户端参数只能作为绑定值。 */
@Component("mysqlTemplateExecutor")
public class MysqlTemplateExecutor implements ToolExecutor {

    @Resource(name = "mysqlTemplateQueryService")
    private MysqlTemplateQueryService queryService;

    @Resource
    private IMysqlExecutionMetricsPort metricsPort;

    @Override
    public ToolBackendType backendType() {
        return ToolBackendType.MYSQL;
    }

    @Override
    public ToolExecutionMode executionMode() {
        return ToolExecutionMode.MYSQL_TEMPLATE;
    }

    @Override
    public ToolExecutionResult execute(ToolExecutionContext context) {
        long startedAt = System.nanoTime();
        ToolExecutionResult result = null;
        try {
            if (context == null || context.getProtocolConfig() == null
                    || context.getProtocolConfig().getMysqlTemplateConfig() == null) {
                result = ToolExecutionResult.failure(context == null ? null : context.getRequestId(),
                        ToolExecutionErrorCode.MISSING_CONFIGURATION,
                        "MySQL template configuration is missing");
                return result;
            }
            McpToolProtocolConfigVO.MysqlTemplateConfig reference =
                    context.getProtocolConfig().getMysqlTemplateConfig();
            MysqlTemplate template = MysqlTemplate.builder()
                    .id(reference.getProtocolId() == null ? reference.getTemplateRef() : String.valueOf(reference.getProtocolId()))
                    .version(reference.getTemplateVersion() == null ? "1" : reference.getTemplateVersion())
                    .name(context.getToolName())
                    .description(context.getToolName())
                    .datasourceRef(reference.getDatasourceRef())
                    .sql(reference.getSql())
                    .parameters(reference.getParameters() == null ? List.<MysqlTemplateParameter>of() : reference.getParameters())
                    .status(Integer.valueOf(1).equals(context.getProtocolConfig().getStatus())
                            ? MysqlTemplateStatus.ENABLED : MysqlTemplateStatus.DISABLED)
                    .policy(reference.getPolicy())
                    .build();
            MysqlQueryResult queryResult = queryService.execute(template, context.argumentsAsMap(),
                    null, context.getRequestId());
            result = ToolExecutionResult.structured(context.getRequestId(), queryResult.getQueryId(),
                    columnMaps(queryResult.getColumns()), rowValues(queryResult), queryResult.isTruncated(),
                    queryResult.getResultBytes());
            return result;
        } catch (MysqlQueryException e) {
            result = ToolExecutionResult.failure(context == null ? null : context.getRequestId(), mapError(e.getCode()),
                    safeMessage(e.getCode()));
            return result;
        } catch (MysqlDomainException e) {
            result = ToolExecutionResult.failure(context == null ? null : context.getRequestId(), mapError(e.getCode()),
                    safeMessage(e.getCode()));
            return result;
        } catch (IllegalArgumentException e) {
            result = ToolExecutionResult.failure(context == null ? null : context.getRequestId(),
                    ToolExecutionErrorCode.INVALID_ARGUMENT, "Invalid MySQL template arguments");
            return result;
        } catch (RuntimeException e) {
            result = ToolExecutionResult.failure(context == null ? null : context.getRequestId(),
                    ToolExecutionErrorCode.BACKEND_ERROR, "MySQL backend execution failed");
            return result;
        } finally {
            if (result != null && metricsPort != null) {
                long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
                String outcome = result.isSuccess() ? "SUCCESS" : result.getErrorCode().name();
                metricsPort.record(outcome, durationMs, result.getRowCount(), result.getResultBytes());
            }
        }
    }

    private static List<Map<String, Object>> columnMaps(List<MysqlQueryResult.MysqlColumn> columns) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (MysqlQueryResult.MysqlColumn column : columns) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("name", column.getName());
            value.put("label", column.getLabel());
            value.put("type", column.getType());
            result.add(value);
        }
        return result;
    }

    private static List<List<Object>> rowValues(MysqlQueryResult result) {
        List<List<Object>> rows = new ArrayList<>();
        for (Map<String, Object> row : result.getRows()) {
            List<Object> values = new ArrayList<>();
            for (MysqlQueryResult.MysqlColumn column : result.getColumns()) {
                values.add(row.get(column.getLabel()));
            }
            rows.add(values);
        }
        return rows;
    }

    private static ToolExecutionErrorCode mapError(String code) {
        if (code == null) return ToolExecutionErrorCode.BACKEND_ERROR;
        return switch (code) {
            case "DATASOURCE_UNAVAILABLE" -> ToolExecutionErrorCode.DATASOURCE_UNAVAILABLE;
            case "PROTOCOL_UNAVAILABLE" -> ToolExecutionErrorCode.PROTOCOL_UNAVAILABLE;
            case "SQL_PARAMETER_ERROR" -> ToolExecutionErrorCode.SQL_PARAMETER_ERROR;
            case "SQL_POLICY_REJECTED" -> ToolExecutionErrorCode.SQL_POLICY_REJECTED;
            case "SQL_SAFETY_FAILED" -> ToolExecutionErrorCode.SQL_POLICY_REJECTED;
            case "SQL_POLICY_NOT_CONFIGURED" -> ToolExecutionErrorCode.SQL_POLICY_NOT_CONFIGURED;
            case "SQL_SYNTAX_ERROR" -> ToolExecutionErrorCode.SQL_SYNTAX_ERROR;
            case "QUERY_TIMEOUT" -> ToolExecutionErrorCode.QUERY_TIMEOUT;
            case "RESULT_LIMIT_EXCEEDED" -> ToolExecutionErrorCode.RESULT_LIMIT_EXCEEDED;
            case "RESOURCE_LIMIT_EXCEEDED" -> ToolExecutionErrorCode.RESOURCE_LIMIT_EXCEEDED;
            case "QUERY_CANCELLED" -> ToolExecutionErrorCode.QUERY_CANCELLED;
            default -> ToolExecutionErrorCode.BACKEND_ERROR;
        };
    }

    private static String safeMessage(String code) {
        return code == null ? "MySQL backend execution failed" : code;
    }
}
