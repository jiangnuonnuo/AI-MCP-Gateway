package cn.bugstack.ai.domain.session.service.message.handler.impl;

import cn.bugstack.ai.domain.session.model.valobj.McpSchemaVO;
import cn.bugstack.ai.domain.session.service.message.handler.IRequestHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 协议握手，建立客户端与服务器的连接
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2025/12/20 11:28
 */
@Slf4j
@Service("initializeHandler")
public class InitializeHandler implements IRequestHandler {

    @Override
    public McpSchemaVO.JSONRPCResponse handle(McpSchemaVO.JSONRPCRequest message) {

        log.info("模拟处理初始化请求");

        return new McpSchemaVO.JSONRPCResponse("2.0", message.id(), Map.of(
                "protocolVersion", "2024-11-05",
                "capabilities", Map.of(
                        "tools", Map.of(),
                        "resources", Map.of()
                ),
                "serverInfo", Map.of(
                        "name", "MCP Weather Proxy Server",
                        "version", "1.0.0"
                )
        ), null);

    }

}
