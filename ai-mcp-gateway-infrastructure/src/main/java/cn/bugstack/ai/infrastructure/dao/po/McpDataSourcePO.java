package cn.bugstack.ai.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Gateway 控制库中的通用数据源记录。
 *
 * <p>该对象只负责数据库字段映射。密码字段始终保存密文，解密主密钥不进入控制库，
 * 连接池和运行时技术参数由应用配置负责。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpDataSourcePO {

    /** 数据源记录主键。 */
    private Long id;

    /** 数据源业务引用，供协议记录绑定。 */
    private String datasourceRef;

    /** 管理侧展示名称。 */
    private String datasourceName;

    /** 数据源驱动类型，例如 mysql。 */
    private String datasourceType;

    /** 不包含密码参数的 JDBC 地址。 */
    private String jdbcUrl;

    /** 目标数据库用户名。 */
    private String username;

    /** 使用密钥加密后的数据库密码。 */
    private String passwordCiphertext;

    /** 每条记录独立的加密随机数。 */
    private String passwordNonce;

    /** 外部密钥引用或版本，不保存密钥本身。 */
    private String encryptionKeyRef;

    /** 数据源状态：0=DISABLED，1=ENABLED。 */
    private Integer status;

    /** 创建时间。 */
    private Date createTime;

    /** 更新时间。 */
    private Date updateTime;
}
