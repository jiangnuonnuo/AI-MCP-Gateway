package cn.bugstack.ai.domain.llm.model.entity;

import cn.bugstack.ai.domain.llm.model.valobj.McpConfigVO;
import cn.bugstack.ai.domain.llm.model.valobj.enums.McpTypeEnumVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 构建对话模型命令
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/4/8 07:01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BuildChatModelCommandEntity {

    /**
     * 网关唯一标识
     */
    private String gatewayId;

    /**
     * mcp 配置信息
     */
    private McpConfigVO mcpConfigVO;

    /**
     * mcp 类型。见 McpTypeEnum，支持 SSE（默认）、STREAMABLE
     * 若为 null 或未指定，默认使用 SSE。
     */
    private McpTypeEnumVO mcpType;

}
