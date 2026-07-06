package cn.bugstack.ai.domain.session.service.distributed;

import cn.bugstack.ai.domain.session.adapter.port.ISessionPort;
import cn.bugstack.ai.domain.session.model.valobj.SessionConfigVO;
import cn.bugstack.ai.domain.session.model.valobj.SessionSyncEventVO;
import cn.bugstack.ai.domain.session.model.valobj.SessionSyncInfoVO;
import cn.bugstack.ai.domain.session.model.valobj.enums.SessionTransportTypeEnumVO;
import cn.bugstack.ai.domain.session.service.ISessionDistributedService;
import jakarta.annotation.Resource;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Sinks;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

/**
 * 分布式会话管理服务
 * <p>
 * 负责会话元数据在 Redis 中的持久化与同步，是 Domain 层与 Infrastructure 层之间的桥梁。
 * 本服务不维护本地会话状态（activeSessions），仅通过 ISessionPort 委托 Redis 操作。
 * <p>
 * 核心能力：
 * <br/>1. buildSessionSyncInfo  — 将本地会话信息转换为可持久化的同步元数据（Instant → long 毫秒时间戳）
 * <br/>2. rebuildLocalSession   — 从 Redis 元数据重建本地 SessionConfigVO（long → Instant）
 * <br/>3. saveSession           — 保存会话元数据到 Redis Map，并发布 CREATE 事件
 * <br/>4. removeSession         — 从 Redis Map 删除元数据，并发布 REMOVE 事件
 * <br/>5. removeSessionSilently — 从 Redis Map 删除元数据，不发布 REMOVE 事件（用于本节点清理过期 Session）
 * <br/>6. loadActiveSessions    — 全量加载 Redis 中有效会话（服务启动恢复用）
 * <br/>7. subscribeSessionSyncEvent — 订阅 Redis Topic，接收其他实例的会话变更事件
 * <br/>8. updateSessionLastAccessedTime — 更新 Redis 中会话的最后访问时间
 * <p>
 * 调用链路：SessionManagementService → ISessionDistributedService → ISessionPort → SessionPort → IRedisService
 *
 * @author xiaofuge bugstack.cn @小傅哥
 */
@Service
public class SessionDistributedService implements ISessionDistributedService {

    @Resource
    private ISessionPort sessionPort;

    /**
     * 构建会话同步信息
     * <p>
     * 将本地会话的核心字段提取为可序列化的同步元数据。
     * 时间字段使用毫秒时间戳（long），避免 Jackson 序列化 Instant 时的兼容问题。
     *
     * @param sessionId     会话唯一标识
     * @param gatewayId     网关ID
     * @param apiKey        API 密钥
     * @param transportType 传输协议类型（SSE / Streamable HTTP）
     * @param nodeId        节点标识，标记该 Session 由哪个节点创建
     * @return 可持久化到 Redis 的会话同步信息
     */
    public SessionSyncInfoVO buildSessionSyncInfo(String sessionId, String gatewayId, String apiKey, SessionTransportTypeEnumVO transportType, String nodeId) {
        long now = System.currentTimeMillis();
        return SessionSyncInfoVO.builder()
                .sessionId(sessionId)
                .gatewayId(gatewayId)
                .apiKey(apiKey)
                .transportType(transportType)
                .nodeId(nodeId)
                .createTime(now)
                .lastAccessedTime(now)
                .active(true)
                .build();
    }

    /**
     * 从同步信息重建本地会话
     * <p>
     * 将 Redis 中存储的会话元数据恢复为本地可用的 SessionConfigVO。
     * 关键操作：新建 Sinks.Many 用于 SSE 推送，时间戳转回 Instant。
     * <p>
     * 应用场景：
     * - 服务启动时，从 Redis 全量加载有效会话后逐个重建
     * - 收到其他实例的 CREATE 事件后，同步到本地实例
     *
     * @param sessionSyncInfoVO Redis 中存储的会话元数据
     * @return 可用于本地消息推送的 SessionConfigVO
     */
    public SessionConfigVO rebuildLocalSession(SessionSyncInfoVO sessionSyncInfoVO) {
        // 重建 SSE 推送通道，客户端连接后可通过此 Sink 接收消息
        Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().multicast().onBackpressureBuffer();
        return SessionConfigVO.builder()
                .sessionId(sessionSyncInfoVO.getSessionId())
                .sink(sink)
                .nodeId(sessionSyncInfoVO.getNodeId())
                .createTime(Instant.ofEpochMilli(sessionSyncInfoVO.getCreateTime()))
                .lastAccessedTime(Instant.ofEpochMilli(sessionSyncInfoVO.getLastAccessedTime()))
                .active(sessionSyncInfoVO.isActive())
                .build();
    }

    /**
     * 保存会话同步信息到 Redis
     * <p>
     * 写入 Redis Map 持久化元数据，同时通过 ISessionPort 发布 CREATE 事件到 Redis Topic，
     * 通知其他应用实例同步该会话到本地。
     *
     * @param sessionSyncInfoVO 会话同步信息
     */
    public void saveSession(SessionSyncInfoVO sessionSyncInfoVO) {
        sessionPort.saveSessionSyncInfo(sessionSyncInfoVO);
    }

    /**
     * 从 Redis 删除会话同步信息，并发布 REMOVE 事件
     * <p>
     * 从 Redis Map 移除元数据，同时通过 ISessionPort 发布 REMOVE 事件到 Redis Topic，
     * 通知其他应用实例清理本地的对应会话。
     *
     * @param sessionId 会话ID
     */
    public void removeSession(String sessionId) {
        sessionPort.removeSessionSyncInfo(sessionId);
    }

    /**
     * 从 Redis 删除会话元数据，不发布 REMOVE 事件
     * <p>
     * 用于本节点清理自己创建的过期 Session 时调用。
     * 仅从 Redis Map 移除元数据，不广播 REMOVE 事件，
     * 避免其他节点误删仍在活跃的 Session。
     * <p>
     * 清理完成后，本节点单独发布 REMOVE 事件通知其他节点清理本地缓存。
     *
     * @param sessionId 会话ID
     */
    @Override
    public void removeSessionSilently(String sessionId) {
        sessionPort.removeSessionSyncInfoSilently(sessionId);
    }

    /**
     * 从 Redis 加载当前所有有效会话
     * <p>
     * 服务启动时调用，全量读取 Redis Map 中的会话元数据，
     * 用于恢复本地的 activeSessions。
     *
     * @return 有效会话元数据列表
     */
    public List<SessionSyncInfoVO> loadActiveSessions() {
        return sessionPort.loadActiveSessions();
    }

    /**
     * 订阅 Redis Session 同步事件
     * <p>
     * 应用启动时由 Trigger 层的 SessionRedisListener 触发订阅，
     * 持续监听 Redis Topic 中的 CREATE / REMOVE 事件，
     * 实现多实例间的会话增量同步。
     *
     * @param consumer 事件消费者，处理 SessionSyncEventVO
     */
    public void subscribeSessionSyncEvent(Consumer<SessionSyncEventVO> consumer) {
        sessionPort.subscribeSessionSyncEvent(consumer);
    }

    /**
     * 更新 Redis 中会话的最后访问时间
     * <p>
     * 客户端每次访问时同步更新 Redis 中的 lastAccessedTime，
     * 确保其他节点重建 Session 时拿到正确的时间戳，
     * 避免因时间不同步导致误删活跃 Session。
     *
     * @param sessionId        会话ID
     * @param lastAccessedTime 最后访问时间（毫秒时间戳）
     */
    @Override
    public void updateSessionLastAccessedTime(String sessionId, long lastAccessedTime) {
        sessionPort.updateSessionLastAccessedTime(sessionId, lastAccessedTime);
    }

    /**
     * 发布会话同步事件到 Redis Topic
     * <p>
     * 用于定时清理过期 Session 后，手动广播 REMOVE 事件通知其他节点清理本地缓存。
     *
     * @param event 会话同步事件
     */
    @Override
    public void publishSessionSyncEvent(SessionSyncEventVO event) {
        sessionPort.publishSessionSyncEvent(event);
    }

}
