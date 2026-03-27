package cn.bugstack.ai.cases.admin;

import cn.bugstack.ai.domain.admin.model.entity.GatewayConfigEntity;
import java.util.List;

/**
 * 运营管理
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/3/26
 */
public interface IAdminManageService {

    List<GatewayConfigEntity> queryGatewayConfigList();

}