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
import cn.bugstack.ai.domain.mysql.adapter.port.IMysqlTemplateRegistry;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplate;
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
    private IMysqlTemplateRegistry mysqlTemplateRegistry;

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
            if (mcpToolProtocolConfigVO == null) {
                mcpToolProtocolConfigVO = mysqlTemplateConfig(toolName);
            }
            if (null == mcpToolProtocolConfigVO) {
                return response(responseId, requestId, ToolExecutionResult.failure(requestId,
                        ToolExecutionErrorCode.TOOL_NOT_FOUND,
                        "Tool is not available"));
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

        } catch (Exception e) {
            log.warn("Tool call request could not be processed, tool={}",
                    message == null || message.params() == null ? null : "provided");
            return response(responseId, requestId, ToolExecutionResult.failure(requestId,
                    ToolExecutionErrorCode.INVALID_ARGUMENT,
                    "Invalid tool call request"));

        }

    }

    private McpToolProtocolConfigVO mysqlTemplateConfig(String toolName) {
        if (mysqlTemplateRegistry == null) return null;
        return mysqlTemplateRegistry.listPublished().stream()
                .filter(template -> toolName.equals(template.getId()) || toolName.equals(template.getName()))
                .findFirst()
                .map(this::toMysqlProtocolConfig)
                .orElse(null);
    }

    private McpToolProtocolConfigVO toMysqlProtocolConfig(MysqlTemplate template) {
        return McpToolProtocolConfigVO.builder()
                .backendType(cn.bugstack.ai.domain.tool.model.valobj.ToolBackendType.MYSQL)
                .executionMode(cn.bugstack.ai.domain.tool.model.valobj.ToolExecutionMode.MYSQL_TEMPLATE)
                .mysqlTemplateConfig(McpToolProtocolConfigVO.MysqlTemplateConfig.builder()
                        .templateRef(template.getId())
                        .templateVersion(template.getVersion())
                        .build())
                .build();
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
