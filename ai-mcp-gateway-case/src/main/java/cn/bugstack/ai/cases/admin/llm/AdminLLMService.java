package cn.bugstack.ai.cases.admin.llm;

import cn.bugstack.ai.api.dto.GatewayLLMRequestDTO;
import cn.bugstack.ai.api.dto.GatewayLLMResponseDTO;
import cn.bugstack.ai.cases.admin.IAdminLLMService;
import cn.bugstack.ai.domain.gateway.model.valobj.GatewayToolConfigVO;
import cn.bugstack.ai.domain.gateway.service.IGatewayToolConfigService;
import cn.bugstack.ai.domain.llm.model.entity.BuildChatModelCommandEntity;
import cn.bugstack.ai.domain.llm.model.valobj.McpConfigVO;
import cn.bugstack.ai.domain.llm.model.valobj.enums.McpTypeEnumVO;
import cn.bugstack.ai.domain.llm.service.ILLMService;
import cn.bugstack.ai.domain.protocol.service.IProtocolStorage;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * LLM 模型对话验证case
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/4/8 07:50
 */
@Slf4j
@Service
public class AdminLLMService implements IAdminLLMService {

    @Value("${server.servlet.context-path}")
    private String baseUrlContextPath;

    @Value("${server.port}")
    private Integer port;

    @Resource
    private ILLMService llmService;

    @Resource
    private IGatewayToolConfigService gatewayToolConfigService;

    @Override
    public GatewayLLMResponseDTO testCallGateway(GatewayLLMRequestDTO requestDTO) {
        log.info("AdminLLMService.testCallGateway {} {} mcpType:{}", requestDTO.getGatewayId(), requestDTO.getMessage(), requestDTO.getMcpType());

        String gatewayId = requestDTO.getGatewayId();

        String baseUrl = "http://localhost:" + port;

        // 解析 MCP 连接类型；默认 SSE
        McpTypeEnumVO mcpType = McpTypeEnumVO.SSE;
        if (StringUtils.isNotBlank(requestDTO.getMcpType())) {
            try {
                mcpType = McpTypeEnumVO.valueOf(requestDTO.getMcpType().toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("不支持的 mcpType:{}，使用默认 SSE", requestDTO.getMcpType());
            }
        }

        // 根据 MCP 类型拼接 endpoint；SSE 带 /sse 后缀，Streamable 不带
        String endpoint = baseUrlContextPath + "/" + gatewayId + "/mcp";
        if (McpTypeEnumVO.SSE == mcpType) {
            endpoint += "/sse";
        }

        // 获取对话模型
        ChatModel chatModel = llmService.getChatModel(gatewayId);

        // 判断是否重新加载 mcp 服务
        if (requestDTO.isReload() || null == chatModel) {

            McpConfigVO mcpConfigVO = McpConfigVO.builder()
                    .baseUri(baseUrl)
                    .sseEndpoint(endpoint)
                    .authApiKey(requestDTO.getAuthApiKey())
                    .timeout(requestDTO.getTimeout())
                    .build();

            BuildChatModelCommandEntity commandEntity = BuildChatModelCommandEntity.builder()
                    .gatewayId(gatewayId)
                    .mcpConfigVO(mcpConfigVO)
                    .mcpType(mcpType)
                    .build();

            llmService.buildChatModel(commandEntity);

            chatModel = llmService.getChatModel(gatewayId);
        }

        String call = chatModel.call(requestDTO.getMessage());

        return GatewayLLMResponseDTO.builder().content(call).build();
    }

}
