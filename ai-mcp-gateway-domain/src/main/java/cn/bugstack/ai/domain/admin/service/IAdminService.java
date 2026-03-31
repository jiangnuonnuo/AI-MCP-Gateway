package cn.bugstack.ai.domain.admin.service;

import cn.bugstack.ai.domain.admin.model.entity.GatewayConfigEntity;
import cn.bugstack.ai.domain.admin.model.entity.GatewayProtocolConfigEntity;
import cn.bugstack.ai.domain.admin.model.entity.GatewayToolConfigEntity;
import cn.bugstack.ai.domain.admin.model.entity.GatewayAuthConfigEntity;
import java.util.List;

/**
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/3/26
 */
public interface IAdminService {

    List<GatewayConfigEntity> queryGatewayConfigList();

    List<GatewayToolConfigEntity> queryGatewayToolList();

    List<GatewayProtocolConfigEntity> queryGatewayProtocolList();

    List<GatewayAuthConfigEntity> queryGatewayAuthList();

}
