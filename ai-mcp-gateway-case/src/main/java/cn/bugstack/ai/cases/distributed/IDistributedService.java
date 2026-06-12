package cn.bugstack.ai.cases.distributed;

import cn.bugstack.ai.domain.session.model.valobj.SessionSyncEventVO;

import java.util.function.Consumer;

/**
 * 分布式服务接口
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/6/9 23:25
 */
public interface IDistributedService {

    void handleSessionSyncEvent(SessionSyncEventVO event);

    void subscribeSessionSyncEvent(Consumer<SessionSyncEventVO> consumer);

}
