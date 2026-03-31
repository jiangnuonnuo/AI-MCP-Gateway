package cn.bugstack.ai.domain.admin.service.impl;

import cn.bugstack.ai.domain.admin.adapter.respository.IAdminRepository;
import cn.bugstack.ai.domain.admin.model.entity.GatewayConfigEntity;
import cn.bugstack.ai.domain.admin.model.entity.GatewayProtocolConfigEntity;
import cn.bugstack.ai.domain.admin.model.entity.GatewayToolConfigEntity;
import cn.bugstack.ai.domain.admin.model.entity.GatewayAuthConfigEntity;
import cn.bugstack.ai.domain.admin.service.IAdminService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/3/26
 */
@Service
public class AdminService implements IAdminService {

    @Resource
    private IAdminRepository adminRepository;

    @Override
    public List<GatewayConfigEntity> queryGatewayConfigList() {
        return adminRepository.queryGatewayConfigList();
    }

    @Override
    public List<GatewayToolConfigEntity> queryGatewayToolList() {
        return adminRepository.queryGatewayToolList();
    }

    @Override
    public List<GatewayProtocolConfigEntity> queryGatewayProtocolList() {
        return adminRepository.queryGatewayProtocolList();
    }

    @Override
    public List<GatewayAuthConfigEntity> queryGatewayAuthList() {
        return adminRepository.queryGatewayAuthList();
    }

}