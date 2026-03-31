package cn.bugstack.ai.domain.admin.adapter.respository;

import cn.bugstack.ai.domain.admin.model.entity.GatewayConfigEntity;
import cn.bugstack.ai.domain.admin.model.entity.GatewayToolConfigEntity;
import java.util.List;

/**
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/3/26 08:36
 */
public interface IAdminRepository {

    List<GatewayConfigEntity> queryGatewayConfigList();

    List<GatewayToolConfigEntity> queryGatewayToolList();

}
