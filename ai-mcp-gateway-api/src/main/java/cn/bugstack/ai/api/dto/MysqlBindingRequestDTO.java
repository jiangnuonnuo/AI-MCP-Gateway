package cn.bugstack.ai.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** Gateway Tool 与 MySQL 模板绑定新建/编辑命令。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MysqlBindingRequestDTO implements Serializable {
    private Long id;
    @NotBlank
    private String gatewayId;
    private Long toolId;
    @NotBlank
    private String toolName;
    @Builder.Default
    private String toolType = "function";
    private String toolDescription;
    @Builder.Default
    private String toolVersion = "1.0.0";
    @NotNull
    private Long protocolId;
    @Builder.Default
    private String protocolType = "mysql";
    private Integer status;
}
