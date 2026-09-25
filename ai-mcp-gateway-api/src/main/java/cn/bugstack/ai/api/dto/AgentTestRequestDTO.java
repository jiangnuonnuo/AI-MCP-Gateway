package cn.bugstack.ai.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** Agent 调度测试请求；通过自然语言触发自动 Tool 发现，不携带手动 Tool 选择。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentTestRequestDTO implements Serializable {
    private String gatewayId;
    private String authApiKey;
    private Integer timeout;
    private String message;
    private boolean reload;
    private String mcpType;
}
