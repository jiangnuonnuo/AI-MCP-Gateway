package cn.bugstack.ai.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/** 管理端手动 Tool 测试请求；不允许携带 SQL、数据源或执行策略覆盖字段。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolManualTestRequestDTO implements Serializable {
    private String gatewayId;
    private String toolName;
    private Map<String, Object> arguments;
}
