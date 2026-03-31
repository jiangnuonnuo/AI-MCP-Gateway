package cn.bugstack.ai.cases.admin;

import cn.bugstack.ai.domain.admin.model.entity.GatewayConfigEntity;
import cn.bugstack.ai.domain.admin.model.entity.GatewayProtocolConfigEntity;
import cn.bugstack.ai.domain.admin.model.entity.GatewayToolConfigEntity;
import cn.bugstack.ai.domain.admin.model.entity.GatewayAuthConfigEntity;
import java.util.List;

/**
 * 运营管理
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/3/26
 */
public interface IAdminManageService {

    List<GatewayConfigEntity> queryGatewayConfigList();

    List<GatewayToolConfigEntity> queryGatewayToolList();

    List<GatewayProtocolConfigEntity> queryGatewayProtocolList();

    List<GatewayAuthConfigEntity> queryGatewayAuthList();

}