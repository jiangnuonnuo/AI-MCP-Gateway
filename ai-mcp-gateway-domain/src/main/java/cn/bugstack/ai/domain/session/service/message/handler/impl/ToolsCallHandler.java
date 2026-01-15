package cn.bugstack.ai.domain.session.service.message.handler.impl;

import cn.bugstack.ai.domain.session.model.valobj.McpSchemaVO;
import cn.bugstack.ai.domain.session.service.message.handler.IRequestHandler;
import cn.bugstack.ai.types.enums.McpErrorCodes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 执行指定的工具调用
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2025/12/20 11:30
 */
@Slf4j
@Service("toolsCallHandler")
public class ToolsCallHandler implements IRequestHandler {

    @Override
    public McpSchemaVO.JSONRPCResponse handle(String gatewayId, McpSchemaVO.JSONRPCRequest message) {

        Object id = message.id();
        Object params = message.params();

        if (!(params instanceof Map)) {

            new McpSchemaVO.JSONRPCResponse.JSONRPCError(McpErrorCodes.INVALID_PARAMS, "Invalid arguments format", null);

            return new McpSchemaVO.JSONRPCResponse("2.0",
                    message.id(),
                    null,
                    new McpSchemaVO.JSONRPCResponse.JSONRPCError(McpErrorCodes.INVALID_PARAMS, "无效参数 - 无效的方法参数", null));
        }

        Map<String, Object> paramsMap = (Map<String, Object>) params;
        String toolName = (String) paramsMap.get("name");
        Object argumentsObj = paramsMap.get("arguments");

        Map<String, Object> arguments = (Map<String, Object>) argumentsObj;

        if ("toUpperCase".equals(toolName)) {
            String word = arguments.get("word").toString();

            return new McpSchemaVO.JSONRPCResponse("2.0", message.id(), Map.of(
                    "content", new Object[]{
                            Map.of(
                                    "type", "text",
                                    "text", word.toUpperCase()
                            )
                    }
            ), null);
        }

        return new McpSchemaVO.JSONRPCResponse("2.0",
                message.id(),
                null,
                new McpSchemaVO.JSONRPCResponse.JSONRPCError(McpErrorCodes.METHOD_NOT_FOUND, "方法未找到 - 方法不存在或不可用", null));
    }

}
