package cn.bugstack.ai.cases.admin;

import cn.bugstack.ai.domain.auth.model.entity.RegisterCommandEntity;

/**
 * 认证配置管理
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/3/24 08:09
 */
public interface IAdminAuthService {

    void saveGatewayAuth(RegisterCommandEntity commandEntity);

}
