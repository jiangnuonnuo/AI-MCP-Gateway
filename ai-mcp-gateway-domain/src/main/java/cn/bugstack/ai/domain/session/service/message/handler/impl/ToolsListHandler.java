package cn.bugstack.ai.domain.session.service.message.handler.impl;

import cn.bugstack.ai.domain.session.model.valobj.McpSchemaVO;
import cn.bugstack.ai.domain.session.service.message.handler.IRequestHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 返回服务器支持的工具列表
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2025/12/20 11:29
 */
@Slf4j
@Service("toolsListHandler")
public class ToolsListHandler  implements IRequestHandler {

    @Override
    public McpSchemaVO.JSONRPCResponse handle(McpSchemaVO.JSONRPCRequest message) {
        return null;
    }

}
