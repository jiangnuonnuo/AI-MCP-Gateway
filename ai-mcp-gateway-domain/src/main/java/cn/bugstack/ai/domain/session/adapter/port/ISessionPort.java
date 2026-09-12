package cn.bugstack.ai.domain.session.adapter.port;

import cn.bugstack.ai.domain.session.model.valobj.SessionSyncEventVO;
import cn.bugstack.ai.domain.session.model.valobj.SessionSyncInfoVO;
import java.util.List;
import java.util.function.Consumer;

/**
 * 回话端口
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/1/30 20:55
 */
public interface ISessionPort {

    /**
     * 保存活跃会话元数据到 Redis
     */
    void saveSessionSyncInfo(SessionSyncInfoVO sessionSyncInfoVO);

    /**
     * 删除活跃会话元数据
     */
    void removeSessionSyncInfo(String sessionId);

    /**
     * 删除活跃会话元数据，不发布 REMOVE 事件
     * <p>
     * 用于本节点清理自己创建的过期 Session，避免广播 REMOVE 事件导致其他节点误删。
     *
     * @param sessionId 会话ID
     */
    void removeSessionSyncInfoSilently(String sessionId);

    /**
     * 加载当前有效的活跃会话
     */
    List<SessionSyncInfoVO> loadActiveSessions();

    /**
     * 发布会话同步事件
     */
    void publishSessionSyncEvent(SessionSyncEventVO event);

    /**
     * 订阅会话同步事件
     */
    int subscribeSessionSyncEvent(Consumer<SessionSyncEventVO> consumer);

    /**
     * 更新 Redis 中会话的最后访问时间
     * <p>
     * 每次客户端访问时调用，确保 Redis 中的 lastAccessedTime 与本地同步，
     * 避免其他节点重建 Session 时拿到过期的时间戳导致误删。
     *
     * @param sessionId        会话ID
     * @param lastAccessedTime 最后访问时间（毫秒时间戳）
     */
    void updateSessionLastAccessedTime(String sessionId, long lastAccessedTime);

}
