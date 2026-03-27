package cn.bugstack.ai.cases.admin.protocol;

import cn.bugstack.ai.cases.admin.IAdminProtocolService;
import cn.bugstack.ai.domain.protocol.model.entity.StorageCommandEntity;
import cn.bugstack.ai.domain.protocol.service.IProtocolStorage;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 协议配置管理
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/3/24 08:12
 */
@Slf4j
@Service
public class AdminProtocolService implements IAdminProtocolService {

    @Resource
    private IProtocolStorage protocolStorage;

    @Override
    public void saveGatewayProtocol(StorageCommandEntity commandEntity) {
        protocolStorage.doStorage(commandEntity);
    }

}
