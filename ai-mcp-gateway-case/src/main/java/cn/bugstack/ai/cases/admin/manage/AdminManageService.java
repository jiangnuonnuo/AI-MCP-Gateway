package cn.bugstack.ai.cases.admin.manage;

import cn.bugstack.ai.cases.admin.IAdminManageService;
import cn.bugstack.ai.domain.admin.model.entity.GatewayConfigEntity;
import cn.bugstack.ai.domain.admin.model.entity.GatewayConfigPageEntity;
import cn.bugstack.ai.domain.admin.model.entity.GatewayConfigQueryEntity;
import cn.bugstack.ai.domain.admin.model.entity.GatewayProtocolConfigEntity;
import cn.bugstack.ai.domain.admin.model.entity.GatewayToolConfigEntity;
import cn.bugstack.ai.domain.admin.model.entity.GatewayAuthConfigEntity;
import cn.bugstack.ai.domain.admin.service.IAdminService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 运营管理实现
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/3/26
 */
@Slf4j
@Service
public class AdminManageService implements IAdminManageService {

    @Resource
    private IAdminService adminService;

    @Override
    public List<GatewayConfigEntity> queryGatewayConfigList() {
        return adminService.queryGatewayConfigList();
    }

    @Override
    public GatewayConfigPageEntity queryGatewayConfigPage(GatewayConfigQueryEntity queryEntity) {
        return adminService.queryGatewayConfigPage(queryEntity);
    }

    @Override
    public List<GatewayToolConfigEntity> queryGatewayToolList() {
        return adminService.queryGatewayToolList();
    }

    @Override
    public List<GatewayProtocolConfigEntity> queryGatewayProtocolList() {
        return adminService.queryGatewayProtocolList();
    }

    @Override
    public List<GatewayAuthConfigEntity> queryGatewayAuthList() {
        return adminService.queryGatewayAuthList();
    }

    @Override
    public List<GatewayToolConfigEntity> queryGatewayToolListByGatewayId(String gatewayId) {
        return adminService.queryGatewayToolListByGatewayId(gatewayId);
    }

    @Override
    public List<GatewayProtocolConfigEntity> queryGatewayProtocolListByGatewayId(String gatewayId) {
        return adminService.queryGatewayProtocolListByGatewayId(gatewayId);
    }

}