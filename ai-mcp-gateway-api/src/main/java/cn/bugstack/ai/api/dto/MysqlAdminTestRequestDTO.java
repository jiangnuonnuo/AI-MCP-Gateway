package cn.bugstack.ai.api.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/** 管理面显式测试命令的资源引用，不携带密码或 SQL。 */
@Data
@NoArgsConstructor
public class MysqlAdminTestRequestDTO implements Serializable {
    private String id;
    private String datasourceRef;
    private String version;
    private Map<String, Object> parameters = new LinkedHashMap<>();

    public MysqlAdminTestRequestDTO(String id, String datasourceRef, String version) {
        this.id = id;
        this.datasourceRef = datasourceRef;
        this.version = version;
    }

    public MysqlAdminTestRequestDTO(String id, String datasourceRef, String version, Map<String, Object> parameters) {
        this.id = id;
        this.datasourceRef = datasourceRef;
        this.version = version;
        this.parameters = parameters;
    }
}
