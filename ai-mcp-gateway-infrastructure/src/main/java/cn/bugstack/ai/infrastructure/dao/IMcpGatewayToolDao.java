package cn.bugstack.ai.infrastructure.dao;

import cn.bugstack.ai.infrastructure.dao.po.McpGatewayToolPO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface IMcpGatewayToolDao {

    int insert(McpGatewayToolPO po);

    int updateProtocolByGatewayId(McpGatewayToolPO po);

    List<McpGatewayToolPO> queryEffectiveTools(String gatewayId);

    /** 查询指定 Gateway 下的启用 Tool，保留协议类型和状态。 */
    List<McpGatewayToolPO> queryEnabledByGatewayId(String gatewayId);

    List<McpGatewayToolPO> queryListByGatewayId(String gatewayId);

    Long queryToolProtocolIdByToolName(McpGatewayToolPO mcpGatewayToolPOReq);

    /** 按 Gateway 与工具名称读取完整绑定，避免丢失协议类型和状态。 */
    McpGatewayToolPO queryByGatewayIdAndToolName(McpGatewayToolPO mcpGatewayToolPOReq);

    List<McpGatewayToolPO> queryToolList(McpGatewayToolPO query);

    Long queryToolListCount(McpGatewayToolPO query);

    List<McpGatewayToolPO> queryAll();

    int deleteByToolId(Long toolId);

    int updateStatusById(McpGatewayToolPO po);

    int deleteById(Long id);

}
