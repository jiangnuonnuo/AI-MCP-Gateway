package cn.bugstack.ai.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** 管理面显式测试命令的资源引用，不携带密码或 SQL。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MysqlAdminTestRequestDTO implements Serializable {
    private String id;
    private String datasourceRef;
    private String version;
}
