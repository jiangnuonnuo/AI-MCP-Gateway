package cn.bugstack.ai.domain.session.service;

import cn.bugstack.ai.domain.session.model.valobj.SessionConfigVO;
import cn.bugstack.ai.domain.session.model.valobj.SessionSyncEventVO;
import cn.bugstack.ai.domain.session.model.valobj.SessionSyncInfoVO;
import cn.bugstack.ai.domain.session.model.valobj.enums.SessionTransportTypeEnumVO;

import java.util.List;
import java.util.function.Consumer;

/**
 * 分布式会话管理服务接口
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/6/9 23:31
 */
public interface ISessionDistributedService {

    /**
     * 构建会话同步信息
     */
    SessionSyncInfoVO buildSessionSyncInfo(String sessionId, String gatewayId, String apiKey, SessionTransportTypeEnumVO transportType);

    /**
     * 从同步信息重建本地会话
     */
    SessionConfigVO rebuildLocalSession(SessionSyncInfoVO sessionSyncInfoVO);

    /**
     * 保存会话同步信息到 Redis
     */
    void saveSession(SessionSyncInfoVO sessionSyncInfoVO);

    /**
     * 从 Redis 删除会话同步信息
     */
    void removeSession(String sessionId);

    /**
     * 从 Redis 加载当前有效的活跃会话
     */
    List<SessionSyncInfoVO> loadActiveSessions();

    /**
     * 订阅 Redis Session 同步事件
     */
    void subscribeSessionSyncEvent(Consumer<SessionSyncEventVO> consumer);

}
