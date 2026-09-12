package cn.bugstack.ai.infrastructure.adapter.port;

import cn.bugstack.ai.domain.session.adapter.port.ISessionPort;
import cn.bugstack.ai.domain.session.model.valobj.SessionSyncEventVO;
import cn.bugstack.ai.domain.session.model.valobj.SessionSyncInfoVO;
import cn.bugstack.ai.infrastructure.redis.IRedisService;
import jakarta.annotation.Resource;
import org.redisson.api.RMap;
import org.redisson.api.RTopic;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 会话端口服务（Infrastructure 层）
 * <p>
 * 实现 Domain 层定义的 ISessionPort 接口，是 Domain 层与 Redis 基础设施之间的适配器。
 * 本类是六边形架构中唯一的 Redis 通信入口，Domain 层不直接依赖 IRedisService。
 * <p>
 * Redis 数据结构：
 * - Redis Map（SESSION_SYNC_MAP）：存储会话元数据，key=sessionId，value=SessionSyncInfoVO
 *   用途：持久化、全量加载、服务重启恢复
 * - Redis Topic（SESSION_SYNC_TOPIC）：发布/订阅会话变更事件
 *   用途：增量同步，通知其他实例创建或删除会话
 * <p>
 * 依赖链路：ISessionPort（Domain 定义）→ SessionPort（Infra 实现）→ IRedisService（Redisson）
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/1/30 20:56
 */
@Component
public class SessionPort implements ISessionPort {

    /**
     * Redis Topic 名称：会话同步事件通道
     * <p>
     * 用于多实例间的增量同步，发布 CREATE / REMOVE 事件
     */
    private static final String SESSION_SYNC_TOPIC = "ai:mcp:gateway:session:sync";

    /**
     * Redis Map 名称：活跃会话元数据存储
     * <p>
     * key = sessionId，value = SessionSyncInfoVO
     * 用于持久化会话元数据，支持服务重启后全量恢复
     */
    private static final String SESSION_SYNC_MAP = "ai:mcp:gateway:session:active";

    @Resource
    private IRedisService redisService;
    /**
     * 保存会话同步信息到 Redis Map
     * <p>
     * 写入元数据后，立即发布 CREATE 事件到 Redis Topic，
     * 通知其他应用实例同步该会话到本地 activeSessions。
     *
     * @param sessionSyncInfoVO 会话同步元数据
     */
    @Override
    public void saveSessionSyncInfo(SessionSyncInfoVO sessionSyncInfoVO) {
        redisService.<String, SessionSyncInfoVO>getMap(SESSION_SYNC_MAP)
                .put(sessionSyncInfoVO.getSessionId(), sessionSyncInfoVO);
        // 写入成功后发布 CREATE 事件，触发其他实例增量同步
        publishSessionSyncEvent(SessionSyncEventVO.builder()
                .eventType(SessionSyncEventVO.EventType.CREATE)
                .sessionSyncInfo(sessionSyncInfoVO)
                .build());
    }

    /**
     * 从 Redis Map 删除会话同步信息
     * <p>
     * 删除元数据后，如果记录存在则发布 REMOVE 事件到 Redis Topic，
     * 通知其他应用实例清理本地 activeSessions 中的对应会话。
     *
     * @param sessionId 待删除的会话ID
     */
    @Override
    public void removeSessionSyncInfo(String sessionId) {
        SessionSyncInfoVO removed = redisService.<String, SessionSyncInfoVO>getMap(SESSION_SYNC_MAP).remove(sessionId);
        if (removed != null) {
            // 记录存在时才发布事件，避免无效广播
            publishSessionSyncEvent(SessionSyncEventVO.builder()
                    .eventType(SessionSyncEventVO.EventType.REMOVE)
                    .sessionSyncInfo(removed)
                    .build());
        }
    }

    /**
     * 从 Redis Map 删除会话元数据，不发布 REMOVE 事件
     * <p>
     * 用于本节点清理自己创建的过期 Session。
     * 仅从 Redis Map 移除元数据，不广播 REMOVE 事件，
     * 避免其他节点在清理广播中误删仍在活跃的同名 Session。
     *
     * @param sessionId 待删除的会话ID
     */
    @Override
    public void removeSessionSyncInfoSilently(String sessionId) {
        redisService.<String, SessionSyncInfoVO>getMap(SESSION_SYNC_MAP).remove(sessionId);
    }

    /**
     * 从 Redis Map 全量加载当前所有有效会话
     * <p>
     * 服务启动时调用，读取 Redis Map 中的全部会话元数据，
     * 供 SessionManagementService 逐个 rebuildLocalSession 恢复到本地。
     *
     * @return 所有存储在 Redis 中的会话元数据列表
     */
    @Override
    public List<SessionSyncInfoVO> loadActiveSessions() {
        RMap<String, SessionSyncInfoVO> sessionMap = redisService.getMap(SESSION_SYNC_MAP);
        return new ArrayList<>(sessionMap.readAllValues());
    }

    /**
     * 发布会话同步事件到 Redis Topic
     * <p>
     * 通过 Redisson RTopic 的 publish 方法广播事件，
     * 所有订阅了该 Topic 的应用实例都会收到消息。
     *
     * @param event 会话同步事件（CREATE / REMOVE）
     */
    @Override
    public void publishSessionSyncEvent(SessionSyncEventVO event) {
        redisService.getTopic(SESSION_SYNC_TOPIC).publish(event);
    }

    /**
     * 订阅 Redis Session 同步事件
     * <p>
     * 应用启动时由 SessionRedisListener 触发调用，
     * 注册消息监听器，持续接收 Redis Topic 中的会话变更事件。
     * <p>
     * 调用链路：SessionRedisListener → IDistributedService → ISessionManagementService
     *           → ISessionDistributedService → ISessionPort.subscribeSessionSyncEvent（本方法）
     *
     * @param consumer 事件消费者，收到消息后回调处理
     * @return 监听器ID，可用于取消订阅
     */
    @Override
    public int subscribeSessionSyncEvent(Consumer<SessionSyncEventVO> consumer) {
        RTopic topic = redisService.getTopic(SESSION_SYNC_TOPIC);
        return topic.addListener(SessionSyncEventVO.class, (channel, msg) -> consumer.accept(msg));
    }

    /**
     * 更新 Redis 中会话的最后访问时间
     * <p>
     * 读取 Redis Map 中的 SessionSyncInfoVO，更新 lastAccessedTime 字段后写回。
     * 不发布 Topic 事件，避免频繁广播造成性能问题。
     *
     * @param sessionId        会话ID
     * @param lastAccessedTime 最后访问时间（毫秒时间戳）
     */
    @Override
    public void updateSessionLastAccessedTime(String sessionId, long lastAccessedTime) {
        RMap<String, SessionSyncInfoVO> sessionMap = redisService.getMap(SESSION_SYNC_MAP);
        SessionSyncInfoVO existing = sessionMap.get(sessionId);
        if (existing != null) {
            SessionSyncInfoVO updated = SessionSyncInfoVO.builder()
                    .sessionId(existing.getSessionId())
                    .gatewayId(existing.getGatewayId())
                    .apiKey(existing.getApiKey())
                    .transportType(existing.getTransportType())
                    .nodeId(existing.getNodeId())
                    .createTime(existing.getCreateTime())
                    .lastAccessedTime(lastAccessedTime)
                    .active(existing.isActive())
                    .build();
            sessionMap.put(sessionId, updated);
        }
    }

}
