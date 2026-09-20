package cn.bugstack.ai.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

/** 管理端返回的数据源摘要；不包含密码密文、nonce 或密钥引用。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MysqlDataSourceDTO implements Serializable {
    private Long id;
    private String datasourceRef;
    private String datasourceName;
    private String datasourceType;
    private String jdbcUrlMasked;
    private String username;
    private Integer status;
    private boolean passwordConfigured;
    private Date createTime;
    private Date updateTime;
}
