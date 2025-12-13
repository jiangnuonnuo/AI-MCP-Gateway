package cn.bugstack.ai.api;

import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

/**
 * MCP 网关服务接口
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2025/12/13 08:53
 */
public interface IMcpGatewayService {

    /**
     * 建立 SSE 连接
     * @param gatewayId 网关ID
     * @return 流式响应
     */
    Flux<ServerSentEvent<String>> establishSSEConnection(String gatewayId) throws Exception;

}
