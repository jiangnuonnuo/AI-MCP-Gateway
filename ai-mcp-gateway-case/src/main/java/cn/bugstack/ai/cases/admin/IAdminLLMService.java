package cn.bugstack.ai.cases.admin;

import cn.bugstack.ai.api.dto.GatewayLLMRequestDTO;
import cn.bugstack.ai.api.dto.GatewayLLMResponseDTO;
import cn.bugstack.ai.api.dto.AgentTestDTO;
import cn.bugstack.ai.api.dto.AgentTestRequestDTO;

/**
 * LLM 对话模型服务，测试 MCP
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/4/8 07:50
 */
public interface IAdminLLMService {

    GatewayLLMResponseDTO testCallGateway(GatewayLLMRequestDTO requestDTO);

    /** 运行统一测试中心的 Agent 调度测试并返回真实 Tool Trace。 */
    AgentTestDTO testAgentGateway(AgentTestRequestDTO requestDTO);

}
