package cn.bugstack.ai.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** MySQL 模板公开参数契约。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MysqlTemplateParameterDTO implements Serializable {
    private String name;
    private String type;
    private boolean required;
    private String description;
}
