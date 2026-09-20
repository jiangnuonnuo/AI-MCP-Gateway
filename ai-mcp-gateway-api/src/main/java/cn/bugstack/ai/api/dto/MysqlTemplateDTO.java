package cn.bugstack.ai.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/** MySQL SQL 模板管理响应。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MysqlTemplateDTO implements Serializable {
    private Long protocolId;
    private String version;
    private String name;
    private String description;
    private String datasourceRef;
    private String sql;
    private List<MysqlTemplateParameterDTO> parameters;
    private Integer maxRows;
    private Long maxResultBytes;
    private Integer maxColumns;
    private Integer timeoutMs;
    private Integer status;
    private Date createTime;
    private Date updateTime;
}
