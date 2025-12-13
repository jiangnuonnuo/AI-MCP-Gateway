package cn.bugstack.ai.trigger.http;

import cn.bugstack.ai.api.IMcpGatewayService;
import cn.bugstack.ai.cases.mcp.IMcpSessionService;
import cn.bugstack.ai.types.enums.ResponseCode;
import cn.bugstack.ai.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import javax.annotation.Resource;

/**
 * MCP 网关服务接口管理
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2025/12/13 08:54
 */
@Slf4j
@RestController
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS})
@RequestMapping("/")
public class McpGatewayController implements IMcpGatewayService {

    @Resource
    private IMcpSessionService mcpSessionService;

    public McpGatewayController() {
        System.out.println("xxxx");
    }

    /**
     * 建立sse连接，创建会话
     * <br/>
     * <a href="http://localhost:8777/api-gateway/test10001/mcp/sse">http://localhost:8777/api-gateway/test10001/mcp/sse</a>
     *
     * @param gatewayId 网关ID
     */
    @GetMapping(value = "{gatewayId}/mcp/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Override
    public Flux<ServerSentEvent<String>> establishSSEConnection(@PathVariable("gatewayId") String gatewayId) throws Exception {
        try {
            log.info("建立 MCP SSE 连接，gatewayId:{}", gatewayId);
            if (StringUtils.isBlank(gatewayId)) {
                log.info("非法参数，gateway is null");
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
            }

            return mcpSessionService.createMcpSession(gatewayId);
        } catch (Exception e) {
            log.error("建立 MCP SSE 连接失败，gatewayId: {}", gatewayId, e);
            throw e;
        }
    }

}
