package cn.bugstack.ai.domain.session.service.management;

import cn.bugstack.ai.domain.session.model.valobj.SessionConfigVO;
import cn.bugstack.ai.domain.session.model.valobj.SessionSyncEventVO;
import cn.bugstack.ai.domain.session.model.valobj.SessionSyncInfoVO;
import cn.bugstack.ai.domain.session.model.valobj.enums.SessionTransportTypeEnumVO;
import cn.bugstack.ai.domain.session.service.ISessionDistributedService;
import cn.bugstack.ai.domain.session.service.ISessionManagementService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Sinks;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 会话管理服务
 * <p>
 * 分布式优化要点：
 * 1. 每个节点生成唯一 nodeId，Session 绑定创建节点
 * 2. 定时清理只处理本节点创建的过期 Session，避免误删其他节点的活跃 Session
 * 3. 清理本节点过期 Session 时不广播 REMOVE 事件（静默删除 Redis 元数据）
 * 4. 清理完本节点后，统一广播一条 REMOVE 事件通知其他节点清理本地缓存
 * 5. getSession 更新 lastAccessedTime 时同步到 Redis，保持分布式时间一致
 * 6. 遍历删除使用先收集再删除的安全模式
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2025/12/2 07:53
 */
@Slf4j
@Service
public class SessionManagementService implements ISessionManagementService {

    /**
     * 会话超时时间（分钟）- 也可以把配置抽取到yml里
     */
    private static final long SESSION_TIMEOUT_MINUTES = 30;

    /**
     * 当前节点标识，用于分布式环境下区分 Session 归属
     * <p>
     * 格式：IP@PID，例如 192.168.1.10@12345
     */
    private final String nodeId;

    /**
     * 定时任务调度
     */
    private final ScheduledExecutorService cleanupScheduler = Executors.newSingleThreadScheduledExecutor();

    /**
     * 活跃回话存储器，key->sessionId，ConcurrentHashMap 确保线程安全
     */
    private final Map<String, SessionConfigVO> activeSessions = new ConcurrentHashMap<>();

    @Resource
    private ISessionDistributedService sessionDistributedService;

    public SessionManagementService() {
        this.nodeId = generateNodeId();
        cleanupScheduler.scheduleAtFixedRate(this::cleanupExpiredSessions, 5, 5, TimeUnit.MINUTES);
        log.info("会话管理服务已启动，节点标识: {}，会话超时时间: {} 分钟", nodeId, SESSION_TIMEOUT_MINUTES);
    }

    /**
     * 生成节点唯一标识
     * <p>
     * 格式：IP@PID，确保分布式环境下每个节点可被唯一识别。
     */
    private String generateNodeId() {
        try {
            String ip = InetAddress.getLocalHost().getHostAddress();
            String pid = java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
            return ip + "@" + pid;
        } catch (Exception e) {
            return "unknown-" + UUID.randomUUID().toString().substring(0, 8);
        }
    }

    @PostConstruct
    public void init() {
        initializeDistributedSessions();
    }

    @Override
    public SessionConfigVO createSession(String gatewayId, String apiKey) {
        return createSession(gatewayId, apiKey, SessionTransportTypeEnumVO.SSE);
    }

    @Override
    public SessionConfigVO createSession(String gatewayId, String apiKey, SessionTransportTypeEnumVO transportType) {
        SessionTransportTypeEnumVO sessionTransportType = transportType == null ? SessionTransportTypeEnumVO.SSE : transportType;
        log.info("创建会话 gatewayId:{} transportType:{} nodeId:{}", gatewayId, sessionTransportType.getCode(), nodeId);

        String sessionId = UUID.randomUUID().toString();
        SessionConfigVO sessionConfigVO = createLocalSession(sessionId, gatewayId, apiKey, sessionTransportType);

        SessionSyncInfoVO sessionSyncInfoVO = sessionDistributedService.buildSessionSyncInfo(sessionId, gatewayId, apiKey, sessionTransportType, nodeId);
        sessionDistributedService.saveSession(sessionSyncInfoVO);

        log.info("创建会话 gatewayId:{} sessionId:{} transportType:{} nodeId:{},当前活跃会话数:{}", gatewayId, sessionId, sessionTransportType.getCode(), nodeId, activeSessions.size());

        return sessionConfigVO;
    }

    @Override
    public void removeSession(String sessionId) {
        log.info("删除会话配置 sessionId:{}", sessionId);
        removeLocalSession(sessionId);
        sessionDistributedService.removeSession(sessionId);
    }

    @Override
    public void removeLocalSession(String sessionId) {
        SessionConfigVO sessionConfigVO = activeSessions.remove(sessionId);

        if (null == sessionConfigVO) {
            log.info("会话{}已不存在于本地实例", sessionId);
            return;
        }

        sessionConfigVO.markInactive();

        try {
            sessionConfigVO.getSink().tryEmitComplete();
        } catch (Exception e) {
            log.warn("关闭会话Sink时出错:{}", e.getMessage());
        }

        log.info("移除本地会话:{},剩余活跃会话数:{}", sessionId, activeSessions.size());
    }

    @Override
    public SessionConfigVO getSession(String sessionId) {
        if (null == sessionId || sessionId.isEmpty()) {
            return null;
        }

        SessionConfigVO sessionConfigVO = activeSessions.get(sessionId);
        if (null != sessionConfigVO && sessionConfigVO.isActive()) {
            sessionConfigVO.updateLastAccessed();
            // 同步更新 Redis 中的最后访问时间，确保分布式环境下的时间一致性
            sessionDistributedService.updateSessionLastAccessedTime(sessionId, System.currentTimeMillis());
            return sessionConfigVO;
        }

        return null;
    }

    @Override
    public void syncSession(SessionSyncInfoVO sessionSyncInfoVO) {
        if (sessionSyncInfoVO == null || StringUtils.isBlank(sessionSyncInfoVO.getSessionId()) || !sessionSyncInfoVO.isActive()) {
            return;
        }

        activeSessions.computeIfAbsent(sessionSyncInfoVO.getSessionId(), key -> {
            log.info("同步远端会话到本地实例 sessionId:{} nodeId:{} transportType:{}", sessionSyncInfoVO.getSessionId(),
                    sessionSyncInfoVO.getNodeId(),
                    sessionSyncInfoVO.getTransportType() == null ? "unknown" : sessionSyncInfoVO.getTransportType().getCode());
            return sessionDistributedService.rebuildLocalSession(sessionSyncInfoVO);
        });
    }

    @Override
    public boolean hasSession(String sessionId) {
        return StringUtils.isNotBlank(sessionId) && activeSessions.containsKey(sessionId);
    }

    @Override
    public void initializeDistributedSessions() {
        int loadCount = 0;
        for (SessionSyncInfoVO sessionSyncInfoVO : sessionDistributedService.loadActiveSessions()) {
            if (sessionSyncInfoVO == null || StringUtils.isBlank(sessionSyncInfoVO.getSessionId()) || !sessionSyncInfoVO.isActive()) {
                continue;
            }

            if (activeSessions.containsKey(sessionSyncInfoVO.getSessionId())) {
                continue;
            }

            activeSessions.put(sessionSyncInfoVO.getSessionId(), sessionDistributedService.rebuildLocalSession(sessionSyncInfoVO));
            loadCount++;
        }

        if (loadCount > 0) {
            log.info("应用启动完成分布式会话初始化，同步会话数:{} 当前本地活跃会话数:{}", loadCount, activeSessions.size());
        } else {
            log.info("应用启动完成分布式会话初始化，未发现可恢复会话");
        }
    }

    @Override
    public void subscribeSessionSyncEvent(Consumer<SessionSyncEventVO> consumer) {
        sessionDistributedService.subscribeSessionSyncEvent(consumer);
    }

    /**
     * 清理过期会话（分布式安全版）
     * <p>
     * 核心策略：
     * 1. 只清理本节点创建的过期 Session（nodeId 匹配），避免误删其他节点的活跃 Session
     * 2. 清理本节点过期 Session 时：先删本地 → 静默删 Redis（不广播）→ 发布 REMOVE 事件通知其他节点清本地缓存
     * 3. 对于其他节点创建的过期 Session，仅移除本地缓存（不操作 Redis）
     * 4. 使用先收集再删除的安全遍历模式，避免 ConcurrentHashMap 弱一致性问题
     */
    public void cleanupExpiredSessions() {
        List<SessionConfigVO> expiredOwnSessions = new ArrayList<>();
        List<String> expiredOtherSessionIds = new ArrayList<>();

        // 第一步：安全遍历，收集过期 Session（区分本节点/其他节点）
        for (Map.Entry<String, SessionConfigVO> entry : activeSessions.entrySet()) {
            SessionConfigVO sessionConfigVO = entry.getValue();

            if (!sessionConfigVO.isActive() || sessionConfigVO.isExpired(SESSION_TIMEOUT_MINUTES)) {
                if (nodeId.equals(sessionConfigVO.getNodeId())) {
                    expiredOwnSessions.add(sessionConfigVO);
                } else {
                    expiredOtherSessionIds.add(sessionConfigVO.getSessionId());
                }
            }
        }

        // 第二步：清理本节点创建的过期 Session
        for (SessionConfigVO session : expiredOwnSessions) {
            String sessionId = session.getSessionId();
            log.info("清理本节点过期会话 sessionId:{} nodeId:{}", sessionId, nodeId);
            // 移除本地会话（关闭 Sink 等）
            removeLocalSession(sessionId);
            // 静默删除 Redis 中的元数据（不广播 REMOVE 事件）
            sessionDistributedService.removeSessionSilently(sessionId);
        }

        // 第三步：统一发布 REMOVE 事件，通知其他节点清理本地缓存
        // Redis 元数据已在第二步删除，此处仅广播事件通知其他节点从 activeSessions 中移除
        for (SessionConfigVO session : expiredOwnSessions) {
            SessionSyncEventVO removeEvent = SessionSyncEventVO.builder()
                    .eventType(SessionSyncEventVO.EventType.REMOVE)
                    .sessionSyncInfo(SessionSyncInfoVO.builder()
                            .sessionId(session.getSessionId())
                            .nodeId(nodeId)
                            .active(false)
                            .build())
                    .build();
            sessionDistributedService.publishSessionSyncEvent(removeEvent);
        }

        // 第四步：对于其他节点创建的过期 Session，仅移除本地缓存（不操作 Redis）
        for (String sessionId : expiredOtherSessionIds) {
            log.info("清理远端过期会话的本地缓存 sessionId:{} (非本节点创建)", sessionId);
            removeLocalSession(sessionId);
        }

        int totalCleaned = expiredOwnSessions.size() + expiredOtherSessionIds.size();
        if (totalCleaned > 0) {
            log.info("清理了 {} 个过期会话（本节点:{} 远端缓存:{}），剩余活跃会话数: {}",
                    totalCleaned, expiredOwnSessions.size(), expiredOtherSessionIds.size(), activeSessions.size());
        }
    }

    @Override
    public void shutdown() {
        log.info("关闭会话管理服务...");

        for (String sessionId : activeSessions.keySet()) {
            removeSession(sessionId);
        }

        cleanupScheduler.shutdown();

        try {
            if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupScheduler.shutdown();
            }
        } catch (InterruptedException e) {
            cleanupScheduler.shutdown();
            Thread.currentThread().interrupt();
        }

        log.info("关闭会话管理服务完成");
    }

    private SessionConfigVO createLocalSession(String sessionId, String gatewayId, String apiKey, SessionTransportTypeEnumVO transportType) {
        Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().multicast().onBackpressureBuffer();

        if (SessionTransportTypeEnumVO.SSE.equals(transportType)) {
            String messageEndpoint = "/api-gateway/" + gatewayId + "/mcp/sse?sessionId=" + sessionId;
            if (StringUtils.isNoneBlank(apiKey)) {
                messageEndpoint += "&api_key=" + apiKey;
            }

            sink.tryEmitNext(ServerSentEvent.<String>builder()
                    .event("endpoint")
                    .data(messageEndpoint)
                    .build());
        }

        SessionConfigVO sessionConfigVO = new SessionConfigVO(sessionId, sink, nodeId);
        activeSessions.put(sessionId, sessionConfigVO);
        return sessionConfigVO;
    }

}
