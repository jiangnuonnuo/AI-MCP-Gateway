package cn.bugstack.ai.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** 动态 SQL Tool 绑定请求；请求中不包含 SQL，SQL 在 tools/call 时传入。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MysqlDynamicBindingRequestDTO implements Serializable {
    private Long id;
    @NotBlank private String gatewayId;
    private Long toolId;
    @NotBlank private String toolName;
    @Builder.Default private String toolType = "function";
    private String toolDescription;
    @Builder.Default private String toolVersion = "1.0.0";
    @NotBlank private String datasourceRef;
    private Integer maxRows;
    private Long maxResultBytes;
    private Integer maxColumns;
    private Integer timeoutMs;
    private Integer status;
}
