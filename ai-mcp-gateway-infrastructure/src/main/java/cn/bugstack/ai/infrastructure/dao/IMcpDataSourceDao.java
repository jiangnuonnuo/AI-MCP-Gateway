package cn.bugstack.ai.infrastructure.dao;

import cn.bugstack.ai.infrastructure.dao.po.McpDataSourcePO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/** mcp_datasource 持久化访问契约。 */
@Mapper
public interface IMcpDataSourceDao {

    /** 新增数据源记录。 */
    int insert(McpDataSourcePO po);

    /** 按主键更新数据源记录。 */
    int updateById(McpDataSourcePO po);

    /** 按业务引用删除数据源记录。 */
    int deleteByDatasourceRef(String datasourceRef);

    /** 按主键读取数据源记录。 */
    McpDataSourcePO queryById(Long id);

    /** 按业务引用读取数据源记录。 */
    McpDataSourcePO queryByDatasourceRef(String datasourceRef);

    /** 读取启用的数据源，供调用阶段失败关闭。 */
    McpDataSourcePO queryEnabledByDatasourceRef(String datasourceRef);

    /** 查询全部数据源记录，供管理端使用。 */
    List<McpDataSourcePO> queryAll();
}
