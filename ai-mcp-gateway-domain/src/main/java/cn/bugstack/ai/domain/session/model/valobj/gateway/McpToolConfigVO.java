package cn.bugstack.ai.domain.session.model.valobj.gateway;

import lombok.*;

import java.util.List;

/**
 * 网关协议映射
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/1/21 08:17
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class McpToolConfigVO {

    /**
     * 所属网关ID
     */
    private String gatewayId;

    /**
     * 所属工具ID
     */
    private Long toolId;

    /**
     * MCP工具名称（如：JavaSDKMCPClient_getCompanyEmployee）
     */
    private String toolName;

    /**
     * 工具描述
     */
    private String toolDescription;

    /**
     * 工具版本
     */
    private String toolVersion;

    /** Tool 业务状态：1 表示 ENABLED，0 表示 DISABLED。空值仅用于兼容未落库状态的 HTTP 旧配置。 */
    private Integer status;

    /**
     * 协议配置
     */
    private McpToolProtocolConfigVO mcpToolProtocolConfigVO;

    /** 判断当前 Gateway 绑定是否可对 MCP Client 暴露。 */
    public boolean isEnabled() {
        return status == null || status == 1;
    }

}
