package cn.bugstack.ai.domain.session.model.valobj;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.HashMap;

/**
 * MCP 架构值对象
 * <p>
 * public：访问修饰符，表示该接口和 record 是公开的，任何地方都可以访问。
 * sealed：限制继承，实现密封接口的类必须在 permits 中声明。
 * interface：定义接口。
 * record：定义不可变数据载体类。
 * implements：表示实现接口。
 * <p>
 * Jackson 注解用于控制 JSON 序列化和反序列化行为。
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2025/12/20 09:07
 */
@Slf4j
public final class McpSchemaVO {

    private static final TypeReference<HashMap<String, Object>> MAP_TYPE_REF = new TypeReference<>() {
    };

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static JSONRPCMessage deserializeJsonRpcMessage(String jsonText)
            throws IOException {

        log.debug("Received JSON message: {}", jsonText);

        var map = objectMapper.readValue(jsonText, MAP_TYPE_REF);

        if (map.containsKey("method") && map.containsKey("id")) {
            return objectMapper.convertValue(map, JSONRPCRequest.class);
        } else if (map.containsKey("method") && !map.containsKey("id")) {
            return objectMapper.convertValue(map, JSONRPCNotification.class);
        } else if (map.containsKey("result") || map.containsKey("error")) {
            return objectMapper.convertValue(map, JSONRPCResponse.class);
        }

        throw new IllegalArgumentException("Cannot deserialize JSONRPCMessage: " + jsonText);
    }

    /**
     * JSON-RPC 2.0 Message Types
     */
    public sealed interface JSONRPCMessage permits JSONRPCRequest, JSONRPCNotification, JSONRPCResponse {

        String jsonrpc();

    }

    /**
     * 请求对象
     *
     * @param jsonrpc 协议版本 2.0
     * @param method  请求方法；initialize、tools/list、tools/call、resources/list
     * @param id      请求ID
     * @param params  请求参数
     */
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JSONRPCRequest(@JsonProperty("jsonrpc") String jsonrpc,
                                 @JsonProperty("method") String method,
                                 @JsonProperty("id") Object id,
                                 @JsonProperty("params") Object params
    ) implements JSONRPCMessage {
    }

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JSONRPCNotification(
            @JsonProperty("jsonrpc") String jsonrpc,
            @JsonProperty("method") String method,
            @JsonProperty("params") Object params) implements JSONRPCMessage {
    }

    /**
     * 响应对象
     *
     * @param jsonrpc 协议版本 2.0
     * @param id      请求ID
     * @param result  响应结果
     * @param error   异常结果
     */
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JSONRPCResponse(
            @JsonProperty("jsonrpc") String jsonrpc,
            @JsonProperty("id") Object id,
            @JsonProperty("result") Object result,
            @JsonProperty("error") JSONRPCError error
    ) implements JSONRPCMessage {
        @JsonInclude(JsonInclude.Include.NON_ABSENT)
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record JSONRPCError(
                @JsonProperty("code") int code,
                @JsonProperty("message") String message,
                @JsonProperty("data") Object data) {
        }
    }

}
