package cn.bugstack.ai.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

/** Gateway Tool 与 MySQL 模板绑定管理响应。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MysqlBindingDTO implements Serializable {
    private Long id;
    private String gatewayId;
    private Long toolId;
    private String toolName;
    private String toolType;
    private String toolDescription;
    private String toolVersion;
    private Long protocolId;
    private String protocolType;
    private Integer status;
    private String executionMode;
    private String datasourceRef;
    private Integer maxRows;
    private Long maxResultBytes;
    private Integer maxColumns;
    private Integer timeoutMs;
    private Date createTime;
    private Date updateTime;
}
