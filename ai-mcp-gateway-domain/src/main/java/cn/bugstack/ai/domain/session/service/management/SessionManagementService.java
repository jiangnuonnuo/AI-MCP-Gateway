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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 会话管理服务
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
        cleanupScheduler.scheduleAtFixedRate(this::cleanupExpiredSessions, 5, 5, TimeUnit.MINUTES);
        log.info("会话管理服务已启动，会话超时时间: {} 分钟", SESSION_TIMEOUT_MINUTES);
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
        log.info("创建会话 gatewayId:{} transportType:{}", gatewayId, sessionTransportType.getCode());

        String sessionId = UUID.randomUUID().toString();
        SessionConfigVO sessionConfigVO = createLocalSession(sessionId, gatewayId, apiKey, sessionTransportType);

        SessionSyncInfoVO sessionSyncInfoVO = sessionDistributedService.buildSessionSyncInfo(sessionId, gatewayId, apiKey, sessionTransportType);
        sessionDistributedService.saveSession(sessionSyncInfoVO);

        log.info("创建会话 gatewayId:{} sessionId:{} transportType:{},当前活跃会话数:{}", gatewayId, sessionId, sessionTransportType.getCode(), activeSessions.size());

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
            log.info("同步远端会话到本地实例 sessionId:{} transportType:{}", sessionSyncInfoVO.getSessionId(),
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

    public void cleanupExpiredSessions() {
        int cleanedCount = 0;

        for (Map.Entry<String, SessionConfigVO> entry : activeSessions.entrySet()) {
            SessionConfigVO sessionConfigVO = entry.getValue();

            if (!sessionConfigVO.isActive() || sessionConfigVO.isExpired(SESSION_TIMEOUT_MINUTES)) {
                removeSession(sessionConfigVO.getSessionId());
                cleanedCount++;
            }

        }

        if (cleanedCount > 0) {
            log.info("清理了 {} 个过期会话，剩余活跃会话数: {}", cleanedCount, activeSessions.size());
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

        SessionConfigVO sessionConfigVO = new SessionConfigVO(sessionId, sink);
        activeSessions.put(sessionId, sessionConfigVO);
        return sessionConfigVO;
    }

}
