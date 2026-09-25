package cn.bugstack.ai.domain.session.service.message.handler.impl;

import cn.bugstack.ai.domain.session.adapter.repository.ISessionRepository;
import cn.bugstack.ai.domain.session.model.valobj.McpSchemaVO;
import cn.bugstack.ai.domain.session.model.valobj.gateway.McpToolConfigVO;
import cn.bugstack.ai.domain.session.service.message.ToolSchemaBuilder;
import cn.bugstack.ai.domain.session.service.message.handler.IRequestHandler;
import cn.bugstack.ai.types.exception.AppException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
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

    @Resource
    private ISessionRepository repository;

    /**
     * {
     * "tools": [
     * {
     * "description": "获取公司雇员信息",
     * "inputSchema": {
     * "additionalProperties": false,
     * "properties": {
     * "xxxRequest01": {
     * "type": "object",
     * "properties": {
     * "city": {
     * "type": "string",
     * "description": "城市名称,如果是中文汉字请先转换为汉语拼音,例如北京:beijing"
     * },
     * "company": {
     * "type": "object",
     * "properties": {
     * "name": {
     * "type": "string",
     * "description": "公司名称"
     * },
     * "type": {
     * "type": "string",
     * "description": "公司类型"
     * }
     * },
     * "required": [
     * "name",
     * "type"
     * ],
     * "description": "公司信息,如果是中文汉字请先转换为汉语拼音,例如北京:jd/alibaba"
     * }
     * },
     * "required": [
     * "city",
     * "company"
     * ]
     * },
     * "xxxRequest02": {
     * "type": "object",
     * "properties": {
     * "employeeCount": {
     * "type": "string",
     * "description": "雇员姓名"
     * }
     * },
     * "required": [
     * "employeeCount"
     * ]
     * }
     * },
     * "required": [
     * "xxxRequest01",
     * "xxxRequest02"
     * ],
     * "type": "object"
     * },
     * "name": "getCompanyEmployee"
     * }
     * ]
     * }
     */
    @Override
    public McpSchemaVO.JSONRPCResponse handle(String gatewayId, McpSchemaVO.JSONRPCRequest message) {

        // 1. 查询网关（gatewayId）下的工具列表配置
        List<McpToolConfigVO> mcpToolConfigVOS;
        try {
            mcpToolConfigVOS = repository.queryMcpGatewayToolConfigListByGatewayId(gatewayId);
        } catch (AppException e) {
            log.warn("Tool discovery control-plane lookup failed, errorCode={}", e.getCode());
            return new McpSchemaVO.JSONRPCResponse(McpSchemaVO.JSONRPC_VERSION, message.id(), null,
                    new McpSchemaVO.JSONRPCResponse.JSONRPCError(-32603,
                            "Gateway configuration is unavailable",
                            Map.of("errorCode", "CONTROL_PLANE_UNAVAILABLE")));
        }

        // 2. 构建工具列表
        List<McpSchemaVO.Tool> tools = ToolSchemaBuilder.build(mcpToolConfigVOS);

        return new McpSchemaVO.JSONRPCResponse("2.0", message.id(), Map.of(
                "tools", tools
        ), null);
    }

}
