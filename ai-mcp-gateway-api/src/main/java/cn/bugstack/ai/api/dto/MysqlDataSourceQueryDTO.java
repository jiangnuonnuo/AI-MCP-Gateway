package cn.bugstack.ai.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** 数据源分页查询条件。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MysqlDataSourceQueryDTO implements Serializable {
    private String datasourceRef;
    private String datasourceName;
    private Integer status;
    private Integer page;
    private Integer rows;
}
