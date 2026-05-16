package cn.bugstack.ai.api;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * MCP Streamable 接口
 */
public interface IMcpStreamableService {

    /**
     * 建立 SSE 连接，返回 endpoint 等初始事件 (GET 请求)
     */
    Flux<ServerSentEvent<String>> handleGet(String headerSessionId, HttpHeaders headers);

    /**
     * 接收 MCP 的请求并返回 JSON RPC 响应 (POST 请求)
     */
    Mono<ResponseEntity<?>> handlePost(String paramSessionId, String headerSessionId, String messageBody, HttpHeaders headers);

    /**
     * 接收 DELETE 请求，关闭会话
     */
    Mono<ResponseEntity<Void>> handleDelete(String sessionId, HttpHeaders headers);

}
