package cn.bugstack.ai.infrastructure.adapter.repository;

import cn.bugstack.ai.domain.admin.adapter.respository.IAdminRepository;
import cn.bugstack.ai.infrastructure.dao.*;
import cn.bugstack.ai.infrastructure.dao.po.McpGatewayToolPO;
import cn.bugstack.ai.infrastructure.dao.po.McpProtocolHttpPO;
import cn.bugstack.ai.infrastructure.dao.po.McpProtocolMappingPO;
import cn.bugstack.ai.infrastructure.dao.po.McpGatewayAuthPO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import cn.bugstack.ai.infrastructure.dao.po.McpGatewayPO;
import cn.bugstack.ai.domain.admin.model.entity.GatewayConfigEntity;
import cn.bugstack.ai.domain.admin.model.entity.GatewayProtocolConfigEntity;
import cn.bugstack.ai.domain.admin.model.entity.GatewayToolConfigEntity;
import cn.bugstack.ai.domain.admin.model.entity.GatewayAuthConfigEntity;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/3/26
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

    @Override
    public List<GatewayToolConfigEntity> queryGatewayToolList() {
        List<cn.bugstack.ai.infrastructure.dao.po.McpGatewayToolPO> mcpGatewayToolPOS = mcpGatewayToolDao.queryAll();
        return mcpGatewayToolPOS.stream().map(po -> GatewayToolConfigEntity.builder()
                .gatewayId(po.getGatewayId())
                .toolId(po.getToolId())
                .toolName(po.getToolName())
                .toolType(po.getToolType())
                .toolDescription(po.getToolDescription())
                .toolVersion(po.getToolVersion())
                .protocolId(po.getProtocolId())
                .protocolType(po.getProtocolType())
                .build()).collect(Collectors.toList());
    }

    @Override
    public List<GatewayProtocolConfigEntity> queryGatewayProtocolList() {
        List<McpProtocolHttpPO> pos = protocolHttpDao.queryAll();
        return pos.stream().map(po -> {
            List<McpProtocolMappingPO> mappings = protocolMappingDao.queryMcpGatewayToolConfigListByProtocolId(po.getProtocolId());
            return GatewayProtocolConfigEntity.builder()
                    .protocolId(po.getProtocolId())
                    .httpUrl(po.getHttpUrl())
                    .httpMethod(po.getHttpMethod())
                    .httpHeaders(po.getHttpHeaders())
                    .timeout(po.getTimeout())
                    .mappings(mappings == null ? null : mappings.stream().map(m -> GatewayProtocolConfigEntity.ProtocolMappingEntity.builder()
                            .mappingType(m.getMappingType())
                            .parentPath(m.getParentPath())
                            .fieldName(m.getFieldName())
                            .mcpPath(m.getMcpPath())
                            .mcpType(m.getMcpType())
                            .mcpDesc(m.getMcpDesc())
                            .isRequired(m.getIsRequired())
                            .sortOrder(m.getSortOrder())
                            .build()).collect(Collectors.toList()))
                    .build();
        }).collect(Collectors.toList());
    }

    @Override
    public List<GatewayAuthConfigEntity> queryGatewayAuthList() {
        List<McpGatewayAuthPO> mcpGatewayAuthPOS = mcpGatewayAuthDao.queryAll();
        return mcpGatewayAuthPOS.stream().map(po -> GatewayAuthConfigEntity.builder()
                .gatewayId(po.getGatewayId())
                .apiKey(po.getApiKey())
                .rateLimit(po.getRateLimit())
                .expireTime(po.getExpireTime())
                .build()).collect(Collectors.toList());
    }

    @Override
    public List<GatewayToolConfigEntity> queryGatewayToolListByGatewayId(String gatewayId) {
        List<McpGatewayToolPO> pos = mcpGatewayToolDao.queryListByGatewayId(gatewayId);
        return pos.stream().map(po -> GatewayToolConfigEntity.builder()
                .gatewayId(po.getGatewayId())
                .toolId(po.getToolId())
                .toolName(po.getToolName())
                .toolType(po.getToolType())
                .toolDescription(po.getToolDescription())
                .toolVersion(po.getToolVersion())
                .protocolId(po.getProtocolId())
                .protocolType(po.getProtocolType())
                .build()).collect(Collectors.toList());
    }

    @Override
    public List<GatewayProtocolConfigEntity> queryGatewayProtocolListByProtocolIds(List<Long> protocolIds) {
        if (protocolIds == null || protocolIds.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        List<McpProtocolHttpPO> pos = protocolHttpDao.queryListByProtocolIds(protocolIds);
        List<McpProtocolMappingPO> mappings = protocolMappingDao.queryListByProtocolIds(protocolIds);
        
        return pos.stream().map(po -> {
            List<McpProtocolMappingPO> protocolMappings = mappings.stream()
                    .filter(m -> m.getProtocolId().equals(po.getProtocolId()))
                    .collect(Collectors.toList());
                    
            return GatewayProtocolConfigEntity.builder()
                    .protocolId(po.getProtocolId())
                    .httpUrl(po.getHttpUrl())
                    .httpMethod(po.getHttpMethod())
                    .httpHeaders(po.getHttpHeaders())
                    .timeout(po.getTimeout())
                    .mappings(protocolMappings.isEmpty() ? null : protocolMappings.stream().map(m -> GatewayProtocolConfigEntity.ProtocolMappingEntity.builder()
                            .mappingType(m.getMappingType())
                            .parentPath(m.getParentPath())
                            .fieldName(m.getFieldName())
                            .mcpPath(m.getMcpPath())
                            .mcpType(m.getMcpType())
                            .mcpDesc(m.getMcpDesc())
                            .isRequired(m.getIsRequired())
                            .sortOrder(m.getSortOrder())
                            .build()).collect(Collectors.toList()))
                    .build();
        }).collect(Collectors.toList());
    }

}
