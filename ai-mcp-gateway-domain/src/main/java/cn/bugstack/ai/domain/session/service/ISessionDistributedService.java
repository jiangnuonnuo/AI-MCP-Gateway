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
    SessionSyncInfoVO buildSessionSyncInfo(String sessionId, String gatewayId, String apiKey, SessionTransportTypeEnumVO transportType, String nodeId);

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
     * 仅从 Redis 删除会话元数据，不发布 REMOVE 事件
     * <p>
     * 用于本节点清理自己创建的过期 Session 时调用，
     * 避免广播 REMOVE 事件导致其他节点误删仍在活跃的同名 Session。
     */
    void removeSessionSilently(String sessionId);

    /**
     * 从 Redis 加载当前有效的活跃会话
     */
    List<SessionSyncInfoVO> loadActiveSessions();

    /**
     * 订阅 Redis Session 同步事件
     */
    void subscribeSessionSyncEvent(Consumer<SessionSyncEventVO> consumer);

    /**
     * 更新 Redis 中会话的最后访问时间
     * <p>
     * 客户端每次访问时同步更新 Redis 中的 lastAccessedTime，
     * 确保其他节点重建 Session 时拿到正确的时间戳。
     *
     * @param sessionId        会话ID
     * @param lastAccessedTime 最后访问时间（毫秒时间戳）
     */
    void updateSessionLastAccessedTime(String sessionId, long lastAccessedTime);

    /**
     * 发布会话同步事件到 Redis Topic
     * <p>
     * 用于定时清理过期 Session 后，手动广播 REMOVE 事件通知其他节点清理本地缓存。
     *
     * @param event 会话同步事件
     */
    void publishSessionSyncEvent(SessionSyncEventVO event);

}
