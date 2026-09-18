package cn.bugstack.ai.infrastructure.dao;

import cn.bugstack.ai.infrastructure.dao.po.McpProtocolMysqlPO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/** mcp_protocol_mysql 持久化访问契约。 */
@Mapper
public interface IMcpProtocolMysqlDao {

    /** 新增 MySQL 协议记录。 */
    int insert(McpProtocolMysqlPO po);

    /** 按协议 ID 更新 MySQL 协议记录。 */
    int updateByProtocolId(McpProtocolMysqlPO po);

    /** 按协议 ID 删除 MySQL 协议记录。 */
    int deleteByProtocolId(Long protocolId);

    /** 按记录主键读取协议。 */
    McpProtocolMysqlPO queryById(Long id);

    /** 按逻辑协议 ID 读取协议。 */
    McpProtocolMysqlPO queryByProtocolId(Long protocolId);

    /** 只读取启用的协议，缺失或停用时由调用方失败关闭。 */
    McpProtocolMysqlPO queryEnabledByProtocolId(Long protocolId);

    /** 查询指定数据源绑定的协议。 */
    List<McpProtocolMysqlPO> queryListByDatasourceId(Long datasourceId);

    /** 查询全部协议记录，供管理端使用。 */
    List<McpProtocolMysqlPO> queryAll();
}
