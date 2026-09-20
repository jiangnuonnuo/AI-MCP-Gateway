package cn.bugstack.ai.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** 数据源新建/编辑命令。password 为空时表示编辑时保留原凭证。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MysqlDataSourceRequestDTO implements Serializable {
    private Long id;
    @NotBlank
    private String datasourceRef;
    @NotBlank
    private String datasourceName;
    @Builder.Default
    private String datasourceType = "mysql";
    @NotBlank
    private String jdbcUrl;
    @NotBlank
    private String username;
    private String password;
    private String encryptionKeyRef;
    private Integer status;
}
