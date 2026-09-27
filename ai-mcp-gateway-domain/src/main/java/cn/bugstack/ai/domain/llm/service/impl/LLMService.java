package cn.bugstack.ai.domain.llm.service.impl;

import cn.bugstack.ai.domain.llm.model.entity.BuildChatModelCommandEntity;
import cn.bugstack.ai.domain.llm.model.valobj.McpConfigVO;
import cn.bugstack.ai.domain.llm.model.valobj.enums.McpTypeEnumVO;
import cn.bugstack.ai.domain.llm.model.valobj.AgentExecutionTrace;
import cn.bugstack.ai.domain.llm.model.valobj.AgentTraceContext;
import cn.bugstack.ai.domain.llm.model.valobj.AgentTraceEvent;
import cn.bugstack.ai.domain.llm.service.ILLMService;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 大模型服务
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/4/8 07:03
 */
@Slf4j
@Service
public class LLMService implements ILLMService {

    private final Map<String, ChatModel> chatModelMap = new HashMap<>();

    // 全局策略映射（构造函数方式，只初始化一次）
    private final Map<McpTypeEnumVO, ToolCallbackBuilderStrategy> strategyMap = new HashMap<>();

    @Resource
    private OpenAiApi openAiApi;

    @Value("${spring.ai.openai.chat.options.model:gpt-4o}")
    private String model;

    // 构造函数，初始化策略映射
    public LLMService() {
        strategyMap.put(McpTypeEnumVO.SSE, new SseToolCallbackBuilderStrategy());
        strategyMap.put(McpTypeEnumVO.STREAMABLE, new StreamableToolCallbackBuilderStrategy());
    }

    @Override
    public void buildChatModel(BuildChatModelCommandEntity commandEntity) {
        log.info("构建对话模型 gatewayId:{} mcpType:{}", commandEntity.getGatewayId(), commandEntity.getMcpType());

        // mcp 配置
        McpConfigVO mcpConfigVO = commandEntity.getMcpConfigVO();

        // model 配置 + mcp 服务
        ToolCallback[] callbacks = buildToolCallback(mcpConfigVO, commandEntity.getMcpType());
        AgentExecutionTrace trace = AgentTraceContext.current();
        if (trace != null) {
            trace.recordDiscoveredTools(Arrays.stream(callbacks == null ? new ToolCallback[0] : callbacks)
                    .filter(java.util.Objects::nonNull)
                    .map(callback -> callback.getToolDefinition().name())
                    .toList());
        }
        ChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(model)
                        .toolCallbacks(wrapToolCallbacks(callbacks, trace))
                        .build())
                .build();

        // 写入缓存
        chatModelMap.put(commandEntity.getGatewayId(), chatModel);
    }

    /**
     * 工具策略接口（直接在内部类实现）
     */
    private interface ToolCallbackBuilderStrategy {
        ToolCallback[] build(McpConfigVO config);
    }

    /**
     * SSE 策略实现
     */
    private static class SseToolCallbackBuilderStrategy implements ToolCallbackBuilderStrategy {
        @Override
        public ToolCallback[] build(McpConfigVO mcpConfigVO) {
            String sseEndPoint = withApiKey(mcpConfigVO.getSseEndpoint(), mcpConfigVO.getAuthApiKey());
            HttpClientSseClientTransport sseClientTransport = HttpClientSseClientTransport
                    .builder(mcpConfigVO.getBaseUri())
                    .sseEndpoint(sseEndPoint)
                    .build();
            McpSyncClient mcpSyncClient = McpClient
                    .sync(sseClientTransport)
                    .requestTimeout(Duration.ofMillis(mcpConfigVO.getTimeout())).build();
            var initialize = mcpSyncClient.initialize();
            log.info("tool sse mcp initialize {}", initialize);
            return SyncMcpToolCallbackProvider.builder().mcpClients(mcpSyncClient).build().getToolCallbacks();
        }

    }

    /**
     * Streamable 策略实现
     */
    private static class StreamableToolCallbackBuilderStrategy implements ToolCallbackBuilderStrategy {
        @Override
        public ToolCallback[] build(McpConfigVO mcpConfigVO) {
            String endpoint = withApiKey(mcpConfigVO.getSseEndpoint(), mcpConfigVO.getAuthApiKey());
            McpClientTransport mcpClientTransport = HttpClientStreamableHttpTransport
                    .builder(mcpConfigVO.getBaseUri())
                    .endpoint(endpoint)
                    .build();
            McpSyncClient mcpSyncClient = McpClient.sync(mcpClientTransport)
                    .requestTimeout(Duration.ofMillis(mcpConfigVO.getTimeout())).build();
            var init_streamable = mcpSyncClient.initialize();
            log.info("tool streamable mcp initialize {}", init_streamable);
            return SyncMcpToolCallbackProvider.builder().mcpClients(mcpSyncClient).build().getToolCallbacks();
        }
    }

    /**
     * MCP 网关通过 api_key 查询参数完成认证。SSE 与 Streamable 必须使用同一认证约定，
     * 并对 Key 做 URI 编码，避免特殊字符破坏 endpoint 查询串。
     */
    private static String withApiKey(String endpoint, String apiKey) {
        if (StringUtils.isBlank(apiKey)) return endpoint;
        String separator = endpoint.contains("?") ? "&" : "?";
        return endpoint + separator + "api_key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
    }

    /**
     * 工具回调分派策略，根据类型选择实现。
     * mcpType 为 null 或不支持时，默认为 SSE。
     */
    public ToolCallback[] buildToolCallback(McpConfigVO mcpConfigVO, McpTypeEnumVO mcpType) {
        ToolCallbackBuilderStrategy strategy = strategyMap.get(mcpType);
        if (strategy == null) {
            // 默认 SSE
            strategy = strategyMap.get(McpTypeEnumVO.SSE);
        }
        return strategy.build(mcpConfigVO);
    }

    private ToolCallback[] wrapToolCallbacks(ToolCallback[] callbacks) {
        return wrapToolCallbacks(callbacks, AgentTraceContext.current());
    }

    /**
     * 将请求作用域 Trace 固定在回调包装器上，避免模型 SDK 在其他执行线程触发 Tool 时丢失 ThreadLocal。
     */
    private ToolCallback[] wrapToolCallbacks(ToolCallback[] callbacks, AgentExecutionTrace trace) {
        if (callbacks == null) return new ToolCallback[0];
        return Arrays.stream(callbacks).filter(java.util.Objects::nonNull)
                .map(callback -> new TracingToolCallback(callback, trace)).toArray(ToolCallback[]::new);
    }

    /** 记录真正进入 Spring AI ToolCallback 的调用，不从最终模型文本反推 Tool 行为。 */
    private static final class TracingToolCallback implements ToolCallback {
        private final ToolCallback delegate;
        private final AgentExecutionTrace traceAtBuild;

        private TracingToolCallback(ToolCallback delegate, AgentExecutionTrace traceAtBuild) {
            this.delegate = delegate;
            this.traceAtBuild = traceAtBuild;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return delegate.getToolDefinition();
        }

        @Override
        public ToolMetadata getToolMetadata() {
            return delegate.getToolMetadata();
        }

        @Override
        public String call(String toolInput) {
            AgentExecutionTrace trace = traceAtBuild == null ? AgentTraceContext.current() : traceAtBuild;
            if (trace == null) return delegate.call(toolInput);
            AgentTraceEvent request;
            try {
                request = trace.toolRequest(getToolDefinition().name(), toolInput);
            } catch (RuntimeException observerFailure) {
                // Trace 只观察正式调用；观察器异常不能改变 Tool 的正式结果。
                return delegate.call(toolInput);
            }
            long startedAt = System.nanoTime();
            try {
                String result = delegate.call(toolInput);
                try {
                    trace.toolSuccess(request, result, startedAt);
                } catch (RuntimeException observerFailure) {
                    log.debug("Agent trace response observation failed toolName={}", getToolDefinition().name());
                }
                return result;
            } catch (RuntimeException exception) {
                try {
                    trace.toolFailure(request, exception, startedAt);
                } catch (RuntimeException observerFailure) {
                    log.debug("Agent trace error observation failed toolName={}", getToolDefinition().name());
                }
                throw exception;
            }
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            return call(toolInput);
        }
    }

    @Override
    public ChatModel getChatModel(String gatewayId) {
        return chatModelMap.get(gatewayId);
    }

}
