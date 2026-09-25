package cn.bugstack.ai.domain.tool.service;

import cn.bugstack.ai.domain.session.adapter.repository.ISessionRepository;
import cn.bugstack.ai.domain.session.model.valobj.gateway.McpToolProtocolConfigVO;
import cn.bugstack.ai.domain.session.service.message.ToolSchemaBuilder;
import cn.bugstack.ai.domain.tool.adapter.port.IToolAccessPolicyPort;
import cn.bugstack.ai.domain.tool.adapter.port.IToolExecutionPort;
import cn.bugstack.ai.domain.tool.model.valobj.ToolExecutionContext;
import cn.bugstack.ai.domain.tool.model.valobj.ToolExecutionErrorCode;
import cn.bugstack.ai.domain.tool.model.valobj.ToolExecutionResult;
import cn.bugstack.ai.types.exception.AppException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Gateway Tool 调用领域入口。
 *
 * <p>MCP Handler 和管理端手动测试都必须经过此入口，确保绑定解析、授权、停用检查和执行器路由只有一份业务语义。</p>
 */
@Service
public class ToolInvocationService {

    @Resource
    private ISessionRepository repository;

    @Resource
    private IToolExecutionPort toolExecutionPort;

    @Resource
    private IToolAccessPolicyPort accessPolicy;

    /** Tool schema 参数校验器，拒绝缺失、多余和错误类型字段后再进入执行器。 */
    @Resource
    private ToolArgumentValidator argumentValidator;

    /**
     * 返回当前 Gateway 已启用且协议可用的 Tool schema。
     */
    public List<cn.bugstack.ai.domain.session.model.valobj.McpSchemaVO.Tool> listTools(String gatewayId) {
        return ToolSchemaBuilder.build(repository.queryMcpGatewayToolConfigListByGatewayId(gatewayId));
    }

    /**
     * 执行一次后端无关的 Tool 调用。
     */
    public ToolExecutionResult execute(String gatewayId, String toolName, Object arguments, String requestId) {
        String effectiveRequestId = requestId == null || requestId.isBlank()
                ? UUID.randomUUID().toString() : requestId;
        if (gatewayId == null || gatewayId.isBlank() || toolName == null || toolName.isBlank()) {
            return ToolExecutionResult.failure(effectiveRequestId, ToolExecutionErrorCode.INVALID_ARGUMENT,
                    "Gateway and tool name are required");
        }
        try {
            if (accessPolicy != null && !accessPolicy.isAllowed(gatewayId, toolName)) {
                return ToolExecutionResult.failure(effectiveRequestId, ToolExecutionErrorCode.ACCESS_DENIED,
                        "Tool access denied");
            }
            McpToolProtocolConfigVO protocol = repository.queryMcpGatewayProtocolConfig(gatewayId, toolName);
            if (protocol == null) {
                return ToolExecutionResult.failure(effectiveRequestId, ToolExecutionErrorCode.TOOL_NOT_FOUND,
                        "Tool is not available");
            }
            if (protocol.getStatus() != null && protocol.getStatus() != 1) {
                return ToolExecutionResult.failure(effectiveRequestId, ToolExecutionErrorCode.TOOL_DISABLED,
                        "Tool is disabled");
            }
            McpToolProtocolConfigVO.MysqlTemplateConfig mysql = protocol.getMysqlTemplateConfig();
            if (mysql != null && mysql.getDatasourceStatus() != null && mysql.getDatasourceStatus() != 1) {
                return ToolExecutionResult.failure(effectiveRequestId, ToolExecutionErrorCode.DATASOURCE_UNAVAILABLE,
                        "Data source is unavailable");
            }
            if (arguments != null && !(arguments instanceof java.util.Map<?, ?>)) {
                return ToolExecutionResult.failure(effectiveRequestId, ToolExecutionErrorCode.INVALID_ARGUMENT,
                        "Tool arguments must be an object");
            }
            if (argumentValidator != null && !argumentValidator.isValid(
                    arguments instanceof java.util.Map<?, ?> map ? toStringKeyMap(map) : null,
                    protocol.getRequestProtocolMappings())) {
                return ToolExecutionResult.failure(effectiveRequestId, ToolExecutionErrorCode.INVALID_ARGUMENT,
                        "Tool arguments do not match the input schema");
            }
            ToolExecutionContext context = ToolExecutionContext.builder()
                    .requestId(effectiveRequestId)
                    .gatewayId(gatewayId)
                    .toolName(toolName)
                    .protocolConfig(protocol)
                    .arguments(arguments)
                    .build();
            return safeExecute(context);
        } catch (AppException e) {
            ToolExecutionErrorCode code = "CONTROL_PLANE_UNAVAILABLE".equals(e.getCode())
                    ? ToolExecutionErrorCode.CONTROL_PLANE_UNAVAILABLE
                    : ToolExecutionErrorCode.MISSING_CONFIGURATION;
            return ToolExecutionResult.failure(effectiveRequestId, code, code.name());
        } catch (RuntimeException e) {
            return ToolExecutionResult.failure(effectiveRequestId, ToolExecutionErrorCode.INVALID_ARGUMENT,
                    "Invalid tool call request");
        }
    }

    private static java.util.Map<String, Object> toStringKeyMap(java.util.Map<?, ?> source) {
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private ToolExecutionResult safeExecute(ToolExecutionContext context) {
        try {
            ToolExecutionResult result = toolExecutionPort.execute(context);
            return result == null
                    ? ToolExecutionResult.failure(context.getRequestId(), ToolExecutionErrorCode.INTERNAL_ERROR,
                    "Tool execution returned no result") : result;
        } catch (RuntimeException e) {
            return ToolExecutionResult.failure(context.getRequestId(), ToolExecutionErrorCode.BACKEND_ERROR,
                    "Tool backend execution failed");
        }
    }
}
