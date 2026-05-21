package cn.bugstack.ai.cases.mcp.streamable.session;

import cn.bugstack.ai.cases.mcp.IMcpSessionService;
import cn.bugstack.ai.types.exception.AppException;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import static cn.bugstack.ai.types.enums.ResponseCode.METHOD_NOT_FOUND;

/**
 * Streamable 会话服务接口
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/5/19 07:55
 */
@Service
public class McpStreamableSessionService implements IMcpSessionService {

    @Override
    public Flux<ServerSentEvent<String>> createMcpSession(String gatewayId, String apiKey) throws Exception {
        throw new AppException(METHOD_NOT_FOUND.getCode(), METHOD_NOT_FOUND.getInfo());
    }

    @Override
    public Flux<ServerSentEvent<String>> getMcpSession(String gatewayId, String apiKey, String sessionId) throws Exception {


        return null;
    }

}
