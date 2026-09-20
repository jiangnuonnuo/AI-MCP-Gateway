package cn.bugstack.ai.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** MySQL Tool 绑定分页查询条件。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MysqlBindingQueryDTO implements Serializable {
    private String gatewayId;
    private String toolName;
    private Long protocolId;
    private Integer status;
    private Integer page;
    private Integer rows;
}
