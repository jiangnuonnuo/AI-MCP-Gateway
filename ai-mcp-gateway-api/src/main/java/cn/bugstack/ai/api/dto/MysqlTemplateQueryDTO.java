package cn.bugstack.ai.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** MySQL 模板分页查询条件。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MysqlTemplateQueryDTO implements Serializable {
    private Long protocolId;
    private String name;
    private String datasourceRef;
    private Integer status;
    private Integer page;
    private Integer rows;
}
