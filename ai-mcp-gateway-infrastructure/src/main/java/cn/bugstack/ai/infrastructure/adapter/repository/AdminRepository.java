package cn.bugstack.ai.infrastructure.adapter.repository;

import cn.bugstack.ai.domain.admin.adapter.respository.IAdminRepository;
import cn.bugstack.ai.infrastructure.dao.*;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import cn.bugstack.ai.infrastructure.dao.po.McpGatewayPO;
import cn.bugstack.ai.domain.admin.model.entity.GatewayConfigEntity;

import java.util.List;
import java.util.stream.Collectors;

/**
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/3/26 08:36
 */
@Slf4j
@Repository
public class AdminRepository implements IAdminRepository {

    @Resource
    private IMcpGatewayAuthDao mcpGatewayAuthDao;

    @Resource
    private IMcpGatewayDao mcpGatewayDao;

    @Resource
    private IMcpGatewayToolDao mcpGatewayToolDao;

    @Resource
    private IMcpProtocolHttpDao protocolHttpDao;

    @Resource
    private IMcpProtocolMappingDao protocolMappingDao;

    @Override
    public List<GatewayConfigEntity> queryGatewayConfigList() {
        List<McpGatewayPO> mcpGatewayPOS = mcpGatewayDao.queryAll();
        return mcpGatewayPOS.stream().map(po -> GatewayConfigEntity.builder()
                .gatewayId(po.getGatewayId())
                .gatewayName(po.getGatewayName())
                .gatewayDesc(po.getGatewayDesc())
                .version(po.getVersion())
                .auth(po.getAuth())
                .status(po.getStatus())
                .build()).collect(Collectors.toList());
    }

}
