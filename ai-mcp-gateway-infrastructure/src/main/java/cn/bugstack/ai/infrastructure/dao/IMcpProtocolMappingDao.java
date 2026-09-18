package cn.bugstack.ai.infrastructure.dao;

import cn.bugstack.ai.infrastructure.dao.po.McpProtocolMappingPO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface IMcpProtocolMappingDao {

    int insert(McpProtocolMappingPO po);

    int deleteById(Long id);

    int deleteByProtocolId(Long protocolId);

    int updateById(McpProtocolMappingPO po);

    McpProtocolMappingPO queryById(Long id);

    List<McpProtocolMappingPO> queryAll();

    List<McpProtocolMappingPO> queryMcpGatewayToolConfigListByProtocolId(Long protocolId);

    /** 按协议类型和逻辑协议 ID 查询 mapping，隔离同值的 HTTP/MySQL ID。 */
    List<McpProtocolMappingPO> queryByProtocolKey(McpProtocolMappingPO query);

    /** 按协议类型和逻辑协议 ID 删除 mapping。 */
    int deleteByProtocolKey(McpProtocolMappingPO query);

    List<McpProtocolMappingPO> queryListByProtocolIds(List<Long> protocolIds);

}
