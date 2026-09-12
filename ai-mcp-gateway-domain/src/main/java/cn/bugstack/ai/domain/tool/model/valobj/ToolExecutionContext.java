package cn.bugstack.ai.domain.tool.model.valobj;

import cn.bugstack.ai.domain.session.model.valobj.gateway.McpToolProtocolConfigVO;
import lombok.Builder;
import lombok.Getter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 一次 Tool 调用的后端无关上下文。
 *
 * <p>Context 只携带领域配置和调用数据，具体执行器不得要求 MCP Client 额外提供数据源地址或凭证。</p>
 */
@Getter
@Builder(toBuilder = true)
public class ToolExecutionContext {

    private final String requestId;
    private final String gatewayId;
    private final String toolName;
    private final ToolBackendType backendType;
    private final ToolExecutionMode executionMode;
    private final McpToolProtocolConfigVO protocolConfig;
    private final Object arguments;
    private final Object backendConfiguration;
    private final Map<String, Object> attributes;

    public ToolExecutionContext(String requestId,
                                String gatewayId,
                                String toolName,
                                ToolBackendType backendType,
                                ToolExecutionMode executionMode,
                                McpToolProtocolConfigVO protocolConfig,
                                Object arguments,
                                Object backendConfiguration,
                                Map<String, Object> attributes) {
        this.requestId = requestId;
        this.gatewayId = gatewayId;
        this.toolName = toolName;
        this.backendType = backendType;
        this.executionMode = executionMode;
        this.protocolConfig = protocolConfig;
        this.arguments = arguments;
        this.backendConfiguration = backendConfiguration;
        this.attributes = attributes == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    }

    /**
     * 兼容已有 HTTP Tool 配置。旧配置未保存 backendType 时，只能依据已存在的 HTTP 配置推断。
     */
    public ToolBackendType resolveBackendType() {
        if (backendType != null && backendType != ToolBackendType.UNKNOWN) {
            return backendType;
        }
        if (protocolConfig != null && protocolConfig.getBackendType() != null
                && protocolConfig.getBackendType() != ToolBackendType.UNKNOWN) {
            return protocolConfig.getBackendType();
        }
        return protocolConfig != null && protocolConfig.getHttpConfig() != null
                ? ToolBackendType.HTTP
                : ToolBackendType.UNKNOWN;
    }

    /**
     * 兼容已有 HTTP Tool。明确配置的执行模式优先，否则根据后端类型推断。
     */
    public ToolExecutionMode resolveExecutionMode() {
        if (executionMode != null && executionMode != ToolExecutionMode.UNKNOWN) {
            return executionMode;
        }
        if (protocolConfig != null && protocolConfig.getExecutionMode() != null
                && protocolConfig.getExecutionMode() != ToolExecutionMode.UNKNOWN) {
            return protocolConfig.getExecutionMode();
        }
        return resolveBackendType() == ToolBackendType.HTTP
                ? ToolExecutionMode.HTTP_REQUEST
                : resolveBackendType() == ToolBackendType.MYSQL
                ? ToolExecutionMode.MYSQL_TEMPLATE
                : ToolExecutionMode.UNKNOWN;
    }

    /**
     * 后端配置是否已经由服务端解析。客户端 arguments 不计入配置判断。
     */
    public boolean hasBackendConfiguration() {
        return protocolConfig != null || backendConfiguration != null || !attributes.isEmpty();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> argumentsAsMap() {
        if (arguments == null) {
            return Collections.emptyMap();
        }
        if (!(arguments instanceof Map<?, ?> rawMap)) {
            throw new IllegalArgumentException("Tool arguments must be an object");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        rawMap.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    public static ToolExecutionContext http(String requestId,
                                             String gatewayId,
                                             String toolName,
                                             McpToolProtocolConfigVO protocolConfig,
                                             Object arguments) {
        return ToolExecutionContext.builder()
                .requestId(requestId)
                .gatewayId(gatewayId)
                .toolName(toolName)
                .backendType(ToolBackendType.HTTP)
                .executionMode(ToolExecutionMode.HTTP_REQUEST)
                .protocolConfig(protocolConfig)
                .arguments(arguments)
                .build();
    }
}
