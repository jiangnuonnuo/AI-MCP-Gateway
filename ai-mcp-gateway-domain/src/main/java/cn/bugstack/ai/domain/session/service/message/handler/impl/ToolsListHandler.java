package cn.bugstack.ai.domain.session.service.message.handler.impl;

import cn.bugstack.ai.domain.session.model.valobj.McpSchemaVO;
import cn.bugstack.ai.domain.session.service.message.handler.IRequestHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 返回服务器支持的工具列表
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2025/12/20 11:29
 */
@Slf4j
@Service("toolsListHandler")
public class ToolsListHandler implements IRequestHandler {

    @Override
    public McpSchemaVO.JSONRPCResponse handle(String gatewayId, McpSchemaVO.JSONRPCRequest message) {
        return new McpSchemaVO.JSONRPCResponse("2.0", message.id(), Map.of(
                "tools", new Object[]{
                        Map.of(
                                "name", "toUpperCase",
                                "description", "小写转大写",
                                "inputSchema", Map.of(
                                        "type", "object",
                                        "properties", Map.of(
                                                "word", Map.of(
                                                        "type", "string",
                                                        "description", "单词，字符串"
                                                )
                                        ),
                                        "required", new String[]{"word"}
                                )
                        )
                }
        ), null);

    }

}
