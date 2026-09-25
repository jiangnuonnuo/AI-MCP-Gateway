package cn.bugstack.ai.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/** 当前 Gateway 可用于测试的 Tool schema。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolTestToolDTO implements Serializable {
    private String name;
    private String description;
    private String version;
    private Map<String, Object> inputSchema;
}
