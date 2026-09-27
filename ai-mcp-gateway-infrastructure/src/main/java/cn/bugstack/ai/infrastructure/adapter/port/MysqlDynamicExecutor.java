package cn.bugstack.ai.infrastructure.adapter.port;

import cn.bugstack.ai.domain.mysql.model.valobj.MysqlQueryResult;
import cn.bugstack.ai.domain.mysql.service.MysqlTemplateQueryService;
import cn.bugstack.ai.domain.session.model.valobj.gateway.McpToolProtocolConfigVO;
import cn.bugstack.ai.domain.tool.executor.ToolExecutor;
import cn.bugstack.ai.domain.tool.model.valobj.ToolBackendType;
import cn.bugstack.ai.domain.tool.model.valobj.ToolExecutionContext;
import cn.bugstack.ai.domain.tool.model.valobj.ToolExecutionErrorCode;
import cn.bugstack.ai.domain.tool.model.valobj.ToolExecutionMode;
import cn.bugstack.ai.domain.tool.model.valobj.ToolExecutionResult;
import cn.bugstack.ai.types.exception.MysqlDomainException;
import cn.bugstack.ai.types.exception.MysqlParameterException;
import cn.bugstack.ai.types.exception.MysqlQueryException;
import cn.bugstack.ai.infrastructure.observability.IMysqlExecutionMetricsPort;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 执行外部传入的 MySQL 动态只读 SQL；数据源和查询策略来自服务端 Tool 配置。 */
@Component("mysqlDynamicExecutor")
public class MysqlDynamicExecutor implements ToolExecutor {

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
        return ToolExecutionMode.MYSQL_DYNAMIC_READONLY;
    }

    @Override
    public ToolExecutionResult execute(ToolExecutionContext context) {
        long startedAt = System.nanoTime();
        ToolExecutionResult result = null;
        try {
            if (context == null || context.getProtocolConfig() == null
                    || context.getProtocolConfig().getMysqlTemplateConfig() == null) {
                result = ToolExecutionResult.failure(context == null ? null : context.getRequestId(),
                        ToolExecutionErrorCode.MISSING_CONFIGURATION, "MySQL dynamic configuration is missing");
                return result;
            }
            McpToolProtocolConfigVO.MysqlTemplateConfig config =
                    context.getProtocolConfig().getMysqlTemplateConfig();
            Map<String, Object> request = context.argumentsAsMap();
            Object sqlValue = request.get("sql");
            Object parameterValue = request.get("parameters");
            if (!(sqlValue instanceof CharSequence) || !(parameterValue instanceof Map<?, ?> rawParameters)
                    || request.size() != 2) {
                result = ToolExecutionResult.failure(context.getRequestId(), ToolExecutionErrorCode.INVALID_ARGUMENT,
                        "Invalid dynamic SQL request");
                return result;
            }
            Map<String, Object> parameters = new LinkedHashMap<>();
            rawParameters.forEach((key, value) -> parameters.put(String.valueOf(key), value));
            MysqlQueryResult queryResult = queryService.executeDynamic(String.valueOf(sqlValue),
                    config.getDatasourceRef(), parameters, config.getPolicy(), context.getRequestId());
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
        } catch (MysqlParameterException e) {
            result = ToolExecutionResult.failure(context == null ? null : context.getRequestId(),
                    ToolExecutionErrorCode.SQL_PARAMETER_ERROR, "SQL_PARAMETER_ERROR");
            return result;
        } catch (IllegalArgumentException e) {
            result = ToolExecutionResult.failure(context == null ? null : context.getRequestId(),
                    ToolExecutionErrorCode.INVALID_ARGUMENT, "Invalid dynamic SQL request");
            return result;
        } catch (RuntimeException e) {
            result = ToolExecutionResult.failure(context == null ? null : context.getRequestId(),
                    ToolExecutionErrorCode.BACKEND_ERROR, "MySQL dynamic execution failed");
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
        if (columns == null) return result;
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
            for (MysqlQueryResult.MysqlColumn column : result.getColumns()) values.add(row.get(column.getLabel()));
            rows.add(values);
        }
        return rows;
    }

    private static ToolExecutionErrorCode mapError(String code) {
        if (code == null) return ToolExecutionErrorCode.BACKEND_ERROR;
        return switch (code) {
            case "INVALID_ARGUMENT" -> ToolExecutionErrorCode.INVALID_ARGUMENT;
            case "DATASOURCE_UNAVAILABLE" -> ToolExecutionErrorCode.DATASOURCE_UNAVAILABLE;
            case "SQL_PARAMETER_ERROR" -> ToolExecutionErrorCode.SQL_PARAMETER_ERROR;
            case "SQL_POLICY_REJECTED", "SQL_SAFETY_FAILED" -> ToolExecutionErrorCode.SQL_POLICY_REJECTED;
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
        return code == null ? "MySQL dynamic execution failed" : code;
    }
}
