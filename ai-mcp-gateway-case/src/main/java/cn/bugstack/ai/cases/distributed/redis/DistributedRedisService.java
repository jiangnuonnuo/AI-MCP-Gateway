package cn.bugstack.ai.cases.distributed.redis;

import cn.bugstack.ai.cases.distributed.IDistributedService;
import cn.bugstack.ai.domain.session.model.valobj.SessionSyncEventVO;
import cn.bugstack.ai.domain.session.model.valobj.SessionSyncInfoVO;
import cn.bugstack.ai.domain.session.service.ISessionManagementService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

/**
 * 分布式会话编排服务（Redis 实现）
 * <p>
 * 位于 Case 层，负责编排分布式会话同步的业务流程。
 * 本类是 Trigger 层与 Domain 层之间的桥梁，不包含领域逻辑，仅做判断和转发。
 * <p>
 * 职责：
 * 1. handleSessionSyncEvent — 接收 Trigger 层转发的 Redis 事件，判断本地是否需要处理，调用 Domain 层执行
 * 2. subscribeSessionSyncEvent — 将 Trigger 层的订阅请求透传到 Domain 层
 * <p>
 * 关键设计：
 * - CREATE 事件：本地已有该 sessionId 则跳过（避免重复同步），否则调用 syncSession 重建
 * - REMOVE 事件：本地没有该 sessionId 则跳过（避免无效操作），否则调用 removeLocalSession 清理
 * - 使用 removeLocalSession 而非 removeSession，避免再次触发 Redis 广播导致事件循环
 * <p>
 * 调用链路：
 * <pre>
 * SessionRedisListener（Trigger）
 *   → IDistributedService.handleSessionSyncEvent（本方法）
 *     → ISessionManagementService.syncSession / removeLocalSession（Domain）
 * </pre>
 *
 * @author xiaofuge bugstack.cn @小傅哥
 */
@Slf4j
@Service
public class DistributedRedisService implements IDistributedService {

    @Resource
    private ISessionManagementService sessionManagementService;

    /**
     * 处理 Redis 会话同步事件
     * <p>
     * 根据 EventType 做不同的处理：
     * <ul>
     *   <li>CREATE：其他实例新建了会话，本地不存在则同步（rebuildLocalSession）</li>
     *   <li>REMOVE：其他实例删除了会话，本地存在则清理（removeLocalSession）</li>
     * </ul>
     * <p>
     * 幂等保护：
     * - CREATE 时本地已存在 → 跳过，不重复创建
     * - REMOVE 时本地不存在 → 跳过，不无效操作
     * - REMOVE 时使用 removeLocalSession 而非 removeSession → 避免再次 publish 到 Redis Topic 造成事件循环
     *
     * @param event Redis Topic 推送的会话同步事件
     */
    public void handleSessionSyncEvent(SessionSyncEventVO event) {
        if (event == null || event.getSessionSyncInfo() == null) {
            return;
        }

        SessionSyncInfoVO sessionSyncInfoVO = event.getSessionSyncInfo();
        if (SessionSyncEventVO.EventType.CREATE.equals(event.getEventType())) {
            // 本地已存在该会话，无需重复同步
            if (sessionManagementService.hasSession(sessionSyncInfoVO.getSessionId())) {
                return;
            }
            // 同步远端会话到本地实例（rebuildLocalSession → 放入 activeSessions）
            sessionManagementService.syncSession(sessionSyncInfoVO);
            return;
        }

        if (SessionSyncEventVO.EventType.REMOVE.equals(event.getEventType())) {
            // 本地不存在该会话，无需清理
            if (!sessionManagementService.hasSession(sessionSyncInfoVO.getSessionId())) {
                return;
            }
            // 仅移除本地会话，不再触发 Redis 广播（避免事件循环）
            sessionManagementService.removeLocalSession(sessionSyncInfoVO.getSessionId());
            log.info("处理分布式会话删除事件 sessionId:{}", sessionSyncInfoVO.getSessionId());
        }
    }

    /**
     * 订阅 Redis Session 同步事件
     * <p>
     * 将 Trigger 层（SessionRedisListener）的订阅请求透传到 Domain 层，
     * 最终通过 ISessionPort 订阅 Redis Topic。
     * <p>
     * 调用链路：
     * SessionRedisListener → IDistributedService.subscribeSessionSyncEvent（本方法）
     *   → ISessionManagementService.subscribeSessionSyncEvent
     *     → ISessionDistributedService.subscribeSessionSyncEvent
     *       → ISessionPort.subscribeSessionSyncEvent → Redis Topic
     *
     * @param consumer 事件消费者，收到 Redis Topic 消息后回调
     */
    public void subscribeSessionSyncEvent(Consumer<SessionSyncEventVO> consumer) {
        sessionManagementService.subscribeSessionSyncEvent(consumer);
    }

}
