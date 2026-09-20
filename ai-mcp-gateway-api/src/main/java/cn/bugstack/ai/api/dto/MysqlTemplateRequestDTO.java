package cn.bugstack.ai.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/** MySQL SQL 模板新建/编辑命令。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MysqlTemplateRequestDTO implements Serializable {
    private Long protocolId;
    @NotBlank
    private String version;
    @NotBlank
    private String name;
    private String description;
    @NotBlank
    private String datasourceRef;
    @NotBlank
    private String sql;
    @Builder.Default
    private List<MysqlTemplateParameterDTO> parameters = List.of();
    private Integer maxRows;
    private Long maxResultBytes;
    private Integer maxColumns;
    private Integer timeoutMs;
    private Integer status;
}
