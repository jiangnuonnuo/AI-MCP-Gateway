package cn.bugstack.ai.domain.session.service.message.handler.impl;

import cn.bugstack.ai.domain.session.adapter.repository.ISessionRepository;
import cn.bugstack.ai.domain.session.model.valobj.McpSchemaVO;
import cn.bugstack.ai.domain.session.model.valobj.gateway.McpToolProtocolConfigVO;
import cn.bugstack.ai.domain.tool.adapter.port.IToolExecutionPort;
import cn.bugstack.ai.domain.tool.adapter.port.IToolAccessPolicyPort;
import cn.bugstack.ai.domain.tool.model.valobj.ToolExecutionContext;
import cn.bugstack.ai.domain.tool.model.valobj.ToolExecutionErrorCode;
import cn.bugstack.ai.domain.tool.model.valobj.ToolExecutionResult;
import cn.bugstack.ai.domain.session.service.message.handler.IRequestHandler;
import cn.bugstack.ai.types.exception.AppException;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 执行指定的工具调用
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2025/12/20 11:30
 */
@Slf4j
@Service("toolsCallHandler")
public class ToolsCallHandler implements IRequestHandler {

    @Resource
    private ISessionRepository repository;

    @Resource
    private IToolExecutionPort toolExecutionPort;

    @Resource
    private IToolAccessPolicyPort accessPolicy;

    @Override
    public McpSchemaVO.JSONRPCResponse handle(String gatewayId, McpSchemaVO.JSONRPCRequest message) {
        Object responseId = message == null ? null : message.id();
        String requestId = responseId == null ? null : String.valueOf(responseId);
        try {
            if (message == null) {
                return response(responseId, requestId, ToolExecutionResult.failure(requestId,
                        ToolExecutionErrorCode.INVALID_ARGUMENT,
                        "MCP request is required"));
            }
            // 1. 转换参数
            McpSchemaVO.CallToolRequest callToolRequest =
                    McpSchemaVO.unmarshalFrom(message.params(), new TypeReference<>() {
                    });

            if (callToolRequest == null || callToolRequest.name() == null || callToolRequest.name().isBlank()) {
                return response(responseId, requestId, ToolExecutionResult.failure(requestId,
                        ToolExecutionErrorCode.INVALID_ARGUMENT,
                        "Tool name is required"));
            }
            Object argumentsObj = callToolRequest.arguments();
            String toolName = callToolRequest.name();

            if (accessPolicy != null && !accessPolicy.isAllowed(gatewayId, toolName)) {
                return response(responseId, requestId, ToolExecutionResult.failure(requestId,
                        ToolExecutionErrorCode.ACCESS_DENIED, "Tool access denied"));
            }

            // 2. 查询协议信息
            McpToolProtocolConfigVO mcpToolProtocolConfigVO = repository.queryMcpGatewayProtocolConfig(gatewayId, toolName);
            if (null == mcpToolProtocolConfigVO) {
                return response(responseId, requestId, ToolExecutionResult.failure(requestId,
                        ToolExecutionErrorCode.TOOL_NOT_FOUND,
                        "Tool is not available"));
            }
            if (mcpToolProtocolConfigVO.getStatus() != null && mcpToolProtocolConfigVO.getStatus() != 1) {
                return response(responseId, requestId, ToolExecutionResult.failure(requestId,
                        ToolExecutionErrorCode.TOOL_DISABLED, "Tool is disabled"));
            }
            McpToolProtocolConfigVO.MysqlTemplateConfig mysqlConfig =
                    mcpToolProtocolConfigVO.getMysqlTemplateConfig();
            if (mysqlConfig != null && mysqlConfig.getDatasourceStatus() != null
                    && mysqlConfig.getDatasourceStatus() != 1) {
                return response(responseId, requestId, ToolExecutionResult.failure(requestId,
                        ToolExecutionErrorCode.DATASOURCE_UNAVAILABLE, "Data source is unavailable"));
            }

            // 3. 交给后端无关的执行端口，Handler 不判断 HTTP、JDBC 或具体后端。
            ToolExecutionContext context = ToolExecutionContext.builder()
                    .requestId(requestId)
                    .gatewayId(gatewayId)
                    .toolName(toolName)
                    .protocolConfig(mcpToolProtocolConfigVO)
                    .arguments(argumentsObj)
                    .build();
            return response(responseId, requestId, toolExecutionPort.execute(context));

        } catch (AppException e) {
            log.warn("Tool call control-plane lookup failed, errorCode={}", e.getCode());
            ToolExecutionErrorCode errorCode = "CONTROL_PLANE_UNAVAILABLE".equals(e.getCode())
                    ? ToolExecutionErrorCode.CONTROL_PLANE_UNAVAILABLE
                    : ToolExecutionErrorCode.MISSING_CONFIGURATION;
            return response(responseId, requestId, ToolExecutionResult.failure(requestId,
                    errorCode, errorCode.name()));
        } catch (Exception e) {
            log.warn("Tool call request could not be processed, tool={}",
                    message == null || message.params() == null ? null : "provided");
            return response(responseId, requestId, ToolExecutionResult.failure(requestId,
                    ToolExecutionErrorCode.INVALID_ARGUMENT,
                    "Invalid tool call request"));

        }

    }

    private McpSchemaVO.JSONRPCResponse response(Object responseId, String requestId, ToolExecutionResult result) {
        ToolExecutionResult safeResult = result == null
                ? ToolExecutionResult.failure(requestId, ToolExecutionErrorCode.INTERNAL_ERROR,
                "Tool execution returned no result")
                : result;
        return new McpSchemaVO.JSONRPCResponse(McpSchemaVO.JSONRPC_VERSION, responseId,
                safeResult.toMcpResult(), null);
    }

}
