package cn.bugstack.ai.cases.admin.llm;

import cn.bugstack.ai.api.dto.GatewayLLMRequestDTO;
import cn.bugstack.ai.api.dto.GatewayLLMResponseDTO;
import cn.bugstack.ai.api.dto.AgentTestDTO;
import cn.bugstack.ai.api.dto.AgentTestRequestDTO;
import cn.bugstack.ai.api.dto.AgentTraceEventDTO;
import cn.bugstack.ai.cases.admin.IAdminLLMService;
import cn.bugstack.ai.domain.llm.model.valobj.AgentExecutionTrace;
import cn.bugstack.ai.domain.llm.model.valobj.AgentTraceContext;
import cn.bugstack.ai.domain.gateway.model.valobj.GatewayToolConfigVO;
import cn.bugstack.ai.domain.gateway.service.IGatewayToolConfigService;
import cn.bugstack.ai.domain.llm.model.entity.BuildChatModelCommandEntity;
import cn.bugstack.ai.domain.llm.model.valobj.McpConfigVO;
import cn.bugstack.ai.domain.llm.model.valobj.enums.McpTypeEnumVO;
import cn.bugstack.ai.domain.llm.service.ILLMService;
import cn.bugstack.ai.domain.protocol.service.IProtocolStorage;
import cn.bugstack.ai.types.security.SensitiveDataSanitizer;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * LLM 模型对话验证case
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/4/8 07:50
 */
@Slf4j
@Service
public class AdminLLMService implements IAdminLLMService {

    @Value("${server.servlet.context-path}")
    private String baseUrlContextPath;

    @Value("${server.port}")
    private Integer port;

    @Resource
    private ILLMService llmService;

    @Resource
    private IGatewayToolConfigService gatewayToolConfigService;

    @Override
    public GatewayLLMResponseDTO testCallGateway(GatewayLLMRequestDTO requestDTO) {
        return GatewayLLMResponseDTO.builder().content(invoke(requestDTO)).build();
    }

    @Override
    public AgentTestDTO testAgentGateway(AgentTestRequestDTO requestDTO) {
        String agentTestId = UUID.randomUUID().toString();
        String requestId = UUID.randomUUID().toString();
        String gatewayId = requestDTO == null ? null : requestDTO.getGatewayId();
        AgentExecutionTrace trace = new AgentExecutionTrace(agentTestId, gatewayId, requestId);
        AgentTraceContext.set(trace);
        long startedAt = System.nanoTime();
        try {
            if (requestDTO == null || requestDTO.getGatewayId() == null || requestDTO.getGatewayId().isBlank()
                    || requestDTO.getMessage() == null || requestDTO.getMessage().isBlank()) {
                trace.finish(null, false, "INVALID_ARGUMENT", "Gateway and message are required");
                return toAgentDTO(trace, requestDTO, requestId, false, "INVALID_ARGUMENT", "Gateway and message are required", startedAt);
            }
            GatewayLLMRequestDTO request = GatewayLLMRequestDTO.builder()
                    .gatewayId(requestDTO.getGatewayId())
                    .authApiKey(requestDTO.getAuthApiKey())
                    .timeout(normalizeTimeout(requestDTO.getTimeout()))
                    .message(requestDTO.getMessage())
                    // Agent 测试默认重新建立 MCP 客户端，确保 tools/list 事件来自本次真实测试。
                    .reload(true)
                    .mcpType(requestDTO.getMcpType())
                    .build();
            String answer = invoke(request);
            boolean toolFailed = trace.snapshotEvents().stream()
                    .anyMatch(event -> "TOOL_CALL_ERROR".equals(event.getType()));
            if (toolFailed) {
                trace.finish(answer, false, "MCP_TOOL_CALL_FAILED", "Agent Tool call failed");
                return toAgentDTO(trace, requestDTO, requestId, false,
                        "MCP_TOOL_CALL_FAILED", "Agent Tool call failed", startedAt);
            }
            trace.finish(answer, true, null, null);
            return toAgentDTO(trace, requestDTO, requestId, true, null, null, startedAt);
        } catch (RuntimeException exception) {
            trace.finish(null, false, "AGENT_EXECUTION_FAILED", "Agent test failed");
            log.warn("Agent test failed gatewayId={} errorType={}", gatewayId, exception.getClass().getSimpleName());
            return toAgentDTO(trace, requestDTO, requestId, false, "AGENT_EXECUTION_FAILED", "Agent test failed", startedAt);
        } finally {
            AgentTraceContext.clear();
        }
    }

    private String invoke(GatewayLLMRequestDTO requestDTO) {
        if (requestDTO == null) throw new IllegalArgumentException("Gateway request is required");
        log.info("AdminLLMService.testCallGateway gatewayId={} mcpType={}", requestDTO.getGatewayId(), requestDTO.getMcpType());

        String gatewayId = requestDTO.getGatewayId();

        String baseUrl = "http://localhost:" + port;

        // 解析 MCP 连接类型；默认 SSE
        McpTypeEnumVO mcpType = McpTypeEnumVO.SSE;
        if (StringUtils.isNotBlank(requestDTO.getMcpType())) {
            try {
                mcpType = McpTypeEnumVO.valueOf(requestDTO.getMcpType().toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("不支持的 mcpType:{}，使用默认 SSE", requestDTO.getMcpType());
            }
        }

        // 根据 MCP 类型拼接 endpoint；SSE 带 /sse 后缀，Streamable 不带
        String endpoint = baseUrlContextPath + "/" + gatewayId + "/mcp";
        if (McpTypeEnumVO.SSE == mcpType) {
            endpoint += "/sse";
        }

        // 获取对话模型
        ChatModel chatModel = llmService.getChatModel(gatewayId);

        // 判断是否重新加载 mcp 服务
        if (requestDTO.isReload() || null == chatModel) {

            McpConfigVO mcpConfigVO = McpConfigVO.builder()
                    .baseUri(baseUrl)
                    .sseEndpoint(endpoint)
                    .authApiKey(requestDTO.getAuthApiKey())
                    .timeout(requestDTO.getTimeout())
                    .build();

            BuildChatModelCommandEntity commandEntity = BuildChatModelCommandEntity.builder()
                    .gatewayId(gatewayId)
                    .mcpConfigVO(mcpConfigVO)
                    .mcpType(mcpType)
                    .build();

            llmService.buildChatModel(commandEntity);

            chatModel = llmService.getChatModel(gatewayId);
        }

        return chatModel.call(requestDTO.getMessage());
    }

    /**
     * 将 Agent 测试的外部运行约束收敛到有限范围，避免无界等待或把非法值传入 MCP SDK。
     */
    private int normalizeTimeout(Integer requestedTimeout) {
        int timeout = requestedTimeout == null ? 30_000 : requestedTimeout;
        return Math.min(120_000, Math.max(1_000, timeout));
    }

    private AgentTestDTO toAgentDTO(AgentExecutionTrace trace, AgentTestRequestDTO request,
                                    String requestId, boolean success, String errorCode,
                                    String errorMessage, long startedAt) {
        return AgentTestDTO.builder()
                .success(success)
                .agentTestId(trace.getAgentTestId())
                .gatewayId(request == null ? null : request.getGatewayId())
                .requestId(requestId)
                .status(trace.getStatus())
                .message(request == null ? null : SensitiveDataSanitizer.sanitize(request.getMessage()))
                .finalAnswer(trace.getFinalAnswer())
                .events(trace.snapshotEvents().stream().map(event -> AgentTraceEventDTO.builder()
                        .sequence(event.getSequence()).type(event.getType()).status(event.getStatus())
                        .toolName(event.getToolName()).requestId(event.getRequestId()).queryId(event.getQueryId())
                        .durationMs(event.getDurationMs()).payload(event.getPayload())
                        .errorCode(event.getErrorCode()).errorMessage(event.getErrorMessage()).build()).collect(Collectors.toList()))
                .requestJson(Map.of("gatewayId", request == null ? "" : String.valueOf(request.getGatewayId()),
                        "message", request == null ? "" : SensitiveDataSanitizer.sanitize(request.getMessage()),
                        "mcpType", request == null || request.getMcpType() == null ? "sse" : request.getMcpType()))
                .responseJson(Map.of("events", trace.snapshotEvents().size(), "finalAnswer", trace.getFinalAnswer() == null ? "" : trace.getFinalAnswer()))
                .durationMs(Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L))
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .build();
    }

}
