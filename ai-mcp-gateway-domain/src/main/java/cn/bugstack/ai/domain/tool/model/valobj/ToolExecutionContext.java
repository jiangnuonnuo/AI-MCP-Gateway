package cn.bugstack.ai.domain.tool.model.valobj;

import cn.bugstack.ai.domain.session.model.valobj.gateway.McpToolProtocolConfigVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 一次 Tool 调用的后端无关上下文。
 *
 * <p>Context 只携带领域配置和调用数据，具体执行器不得要求 MCP Client 额外提供数据源地址或凭证。</p>
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ToolExecutionContext {

    /** 调用请求标识。 */
    private String requestId;

    /** Gateway 标识。 */
    private String gatewayId;

    /** Tool 名称。 */
    private String toolName;

    /** Tool 后端类型。 */
    private ToolBackendType backendType;

    /** Tool 执行模式。 */
    private ToolExecutionMode executionMode;

    /** 服务端解析后的协议配置。 */
    private McpToolProtocolConfigVO protocolConfig;

    /** MCP Client 传入的调用参数。 */
    private Object arguments;

    /** 已解析的后端配置，不接受客户端直接提供的地址或凭证。 */
    private Object backendConfiguration;

    /** 执行链路扩展属性。 */
    @Builder.Default
    private Map<String, Object> attributes = Collections.emptyMap();

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
        return protocolConfig != null || backendConfiguration != null || !getAttributes().isEmpty();
    }

    /**
     * 返回不可变的扩展属性快照。
     */
    public Map<String, Object> getAttributes() {
        if (attributes == null || attributes.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    }

    /**
     * 接收扩展属性时复制输入，避免调用方修改执行上下文。
     */
    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
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
