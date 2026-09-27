package cn.bugstack.ai.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * MySQL 协议控制库记录。
 *
 * <p>SQL 正文、数据源绑定和业务资源上限收敛在同一协议记录中，不再通过独立模板表
 * 复制 Tool 身份字段。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpProtocolMysqlPO {

    /** 协议记录主键。 */
    private Long id;

    /** 多协议 Tool 绑定使用的逻辑协议 ID。 */
    private Long protocolId;

    /** MySQL 执行模式：TEMPLATE 或 DYNAMIC_READONLY。 */
    private String executionMode;

    /** 关联 mcp_datasource 的物理外键。 */
    private Long datasourceId;

    /** 模板模式保存的只读 SQL；动态模式为空。 */
    private String sqlText;

    /** 单次查询最大返回行数。 */
    private Integer maxRows;

    /** 单次查询最大结果字节数。 */
    private Long maxResultBytes;

    /** 单次查询最大返回列数。 */
    private Integer maxColumns;

    /** 单次查询超时时间，单位毫秒。 */
    private Integer timeoutMs;

    /** 协议状态：0=DISABLED，1=ENABLED。 */
    private Integer status;

    /** 创建时间。 */
    private Date createTime;

    /** 更新时间。 */
    private Date updateTime;
}
