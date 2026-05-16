package cn.bugstack.ai.trigger.http;

import cn.bugstack.ai.api.IMcpStreamableService;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP Streamable 控制器
 */
@Slf4j
@RestController
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS})
@RequestMapping("/mcp")
public class McpStreamableController implements IMcpStreamableService {

    // 本地保存 SessionId 和对应的 SSE 发射器 (Sink)
    private final Map<String, Sinks.Many<ServerSentEvent<String>>> sessions = new ConcurrentHashMap<>();

    /**
     * 模拟 McpTransportContextExtractor 的功能，提取请求头或认证信息作为上下文
     */
    private Map<String, String> extractContext(HttpHeaders headers) {
        Map<String, String> context = new HashMap<>();
        if (headers != null) {
            headers.forEach((key, value) -> context.put(key, String.join(",", value)));
        }
        return context;
    }

    @Override
    @GetMapping(produces = "text/event-stream")
    public Flux<ServerSentEvent<String>> handleGet(
            @RequestHeader(value = "Mcp-Session-Id", required = false) String headerSessionId,
            @RequestHeader HttpHeaders headers) {
        
        String sessionId = headerSessionId != null ? headerSessionId : UUID.randomUUID().toString();
        Map<String, String> transportContext = extractContext(headers);
        log.info("MCP SSE 连接建立，分配/使用 sessionId: {}, context: {}", sessionId, transportContext);

        // 复用已有的 Sink（Streamable 模式下先 POST initialize 时已创建），或创建新的
        Sinks.Many<ServerSentEvent<String>> sink = sessions.computeIfAbsent(sessionId, 
                k -> Sinks.many().unicast().onBackpressureBuffer());

        /*// 1. 发送 endpoint 事件，告诉客户端后续 POST 的地址
        // 如果为了兼容，可以做如下设计；
        sink.tryEmitNext(ServerSentEvent.<String>builder()
                .id(sessionId)
                .event("endpoint")
                .data("/api-gateway/mcp?sessionId=" + sessionId)
                .build());*/

        // 2. 返回 Flux 并处理断开连接时的清理
        // 模拟 contextExtractor 将上下文信息写入 Reactor Context，供下游或过滤器使用
        return sink.asFlux()
                .contextWrite(ctx -> ctx.put("MCP_TRANSPORT_CONTEXT", transportContext))
                .doOnCancel(() -> {
                    log.info("MCP SSE 连接客户端取消, sessionId: {}", sessionId);
                    sessions.remove(sessionId);
                })
                .doOnTerminate(() -> {
                    log.info("MCP SSE 连接终止, sessionId: {}", sessionId);
                    sessions.remove(sessionId);
                });
    }

    @Override
    @PostMapping(consumes = "application/json")
    public Mono<ResponseEntity<?>> handlePost(
            @RequestParam(value = "sessionId", required = false) String paramSessionId,
            @RequestHeader(value = "Mcp-Session-Id", required = false) String headerSessionId,
            @RequestBody String messageBody,
            @RequestHeader HttpHeaders headers) {
            
        String sessionId = paramSessionId != null ? paramSessionId : headerSessionId;
        Map<String, String> transportContext = extractContext(headers);
        log.info("MCP 收到消息，sessionId: {}, context: {}, message: {}", sessionId, transportContext, messageBody);

        try {
            JSONObject jsonRpcRequest = JSON.parseObject(messageBody);
            Object id = jsonRpcRequest.get("id");
            String method = jsonRpcRequest.getString("method");

            // 对于 initialize 请求，可能还没有分配好双向绑定的 sessionId（或者此时客户端还没有将 sessionId 带过来）
            // 在 Spring AI MCP 的标准实现中，如果它是 initialize，我们会为它建立双向会话并分配新的 session
            if ("initialize".equals(method)) {
                if (sessionId == null) {
                    // 如果客户端此时没有 sessionId，我们在 initialize 阶段为其补发或者依赖之前建立好的
                    // （如果客户端严格按照 SSE endpoint 的参数，通常会带上，但为了容错可以这里补充分配）
                    sessionId = UUID.randomUUID().toString();
                }
                
                // 确保 session 池中有该 session 的 Sink（兼容客户端先 POST initialize 的场景）
                if (!sessions.containsKey(sessionId)) {
                     Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().unicast().onBackpressureBuffer();
                     sessions.put(sessionId, sink);
                }

                JSONObject response = new JSONObject();
                response.put("jsonrpc", "2.0");
                if (id != null) {
                    response.put("id", id);
                }

                JSONObject result = new JSONObject();
                result.put("protocolVersion", "2024-11-05");
                
                JSONObject capabilities = new JSONObject();
                capabilities.put("tools", new JSONObject());
                result.put("capabilities", capabilities);

                JSONObject serverInfo = new JSONObject();
                serverInfo.put("name", "MCP-Server");
                serverInfo.put("version", "1.0.0");
                result.put("serverInfo", serverInfo);

                response.put("result", result);

                // 在 initialize 返回时，必须在 Header 中返回 Mcp-Session-Id，告诉客户端分配好的 session
                return Mono.<ResponseEntity<?>>just(ResponseEntity.ok()
                        .header("Mcp-Session-Id", sessionId)
                        .body(response.toJSONString()))
                        .contextWrite(ctx -> ctx.put("MCP_TRANSPORT_CONTEXT", transportContext));
            }

            // 对于非 initialize 的请求，必须校验 sessionId 是否合法且活跃
            if (sessionId == null || !sessions.containsKey(sessionId)) {
                log.warn("无效的或已断开的 sessionId: {}", sessionId);
                return Mono.just(ResponseEntity.badRequest().body("{\"error\": \"Invalid or missing sessionId\"}"));
            }

            Sinks.Many<ServerSentEvent<String>> sink = sessions.get(sessionId);

            JSONObject response = new JSONObject();
            response.put("jsonrpc", "2.0");
            if (id != null) {
                response.put("id", id);
            }
            
            if ("ping".equals(method)) {
                response.put("result", new JSONObject());
            } else {
                // 模拟固定返回
                JSONObject result = new JSONObject();
                
                if ("tools/list".equals(method)) {
                    JSONObject toolsResponse = new JSONObject();
                    com.alibaba.fastjson.JSONArray toolsArray = new com.alibaba.fastjson.JSONArray();
                    
                    // 构造一个模拟工具，以防客户端因工具列表为空而异常
                    JSONObject dummyTool = new JSONObject();
                    dummyTool.put("name", "dummy_tool");
                    dummyTool.put("description", "这是一个用于测试的模拟工具");

                    JSONObject inputSchema = new JSONObject();
                    inputSchema.put("type", "object");
                    inputSchema.put("properties", new JSONObject());

                    dummyTool.put("inputSchema", inputSchema);

                    toolsArray.add(dummyTool);
                    
                    toolsResponse.put("tools", toolsArray);
                    response.put("result", toolsResponse);
                } else if ("tools/call".equals(method)) {
                    JSONObject toolCallResponse = new JSONObject();
                    com.alibaba.fastjson.JSONArray content = new com.alibaba.fastjson.JSONArray();
                    JSONObject contentItem = new JSONObject();
                    contentItem.put("type", "text");
                    contentItem.put("text", "这是一个固定的工具调用结果数据");
                    content.add(contentItem);
                    toolCallResponse.put("content", content);
                    response.put("result", toolCallResponse);
                } else {
                    result.put("data", "这是一个固定的结果数据");
                    result.put("method", method);
                    response.put("result", result);
                }
            }

            // 对于非 initialize 请求，通过 SSE 连接推送 JSON-RPC 响应
            // 同样需要指定事件 id (通常为 sessionId 或 messageId)
            sink.tryEmitNext(ServerSentEvent.<String>builder()
                    .id(sessionId)
                    .event("message")
                    .data(response.toJSONString())
                    .build());

            // POST 接口本身只返回 HTTP 202 Accepted
            return Mono.<ResponseEntity<?>>just(ResponseEntity.accepted().build())
                    .contextWrite(ctx -> ctx.put("MCP_TRANSPORT_CONTEXT", transportContext));

        } catch (Exception e) {
            log.error("处理 MCP 消息失败", e);
            return Mono.just(ResponseEntity.badRequest().body("{\"error\": \"Invalid request\"}"));
        }
    }

    @Override
    @DeleteMapping
    public Mono<ResponseEntity<Void>> handleDelete(
            @RequestParam(value = "sessionId", required = false) String sessionId,
            @RequestHeader HttpHeaders headers) {
        
        Map<String, String> transportContext = extractContext(headers);
        
        if (sessionId != null && sessions.containsKey(sessionId)) {
            log.info("MCP 收到关闭请求，sessionId: {}, context: {}", sessionId, transportContext);
            Sinks.Many<ServerSentEvent<String>> sink = sessions.get(sessionId);
            // 结束事件流
            sink.tryEmitComplete();
            sessions.remove(sessionId);
            return Mono.just(ResponseEntity.ok().<Void>build())
                    .contextWrite(ctx -> ctx.put("MCP_TRANSPORT_CONTEXT", transportContext));
        }
        return Mono.just(ResponseEntity.notFound().build());
    }

}
