package cn.bugstack.ai.trigger.listener;

import cn.bugstack.ai.cases.distributed.IDistributedService;
import cn.bugstack.ai.domain.session.model.valobj.SessionSyncEventVO;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Redis Session 同步监听器
 * <p>
 * 位于 Trigger 层，负责订阅 Redis Topic 中的会话同步事件，并将事件转发到 Case 层处理。
 * <p>
 * 触发时机：应用启动时通过 @PostConstruct 自动订阅 Redis Topic
 * <p>
 * 调用链路（符合 DDD 分层规范，Trigger 不直接调用 Port）：
 * <pre>
 * SessionRedisListener（Trigger 层）
 *   → IDistributedService（Case 层，编排）
 *     → ISessionManagementService（Domain 层，领域逻辑）
 *       → ISessionDistributedService（Domain 层，分布式委托）
 *         → ISessionPort（Domain 层定义接口）
 *           → SessionPort（Infrastructure 层实现，调 Redis）
 * </pre>
 * <p>
 * 事件处理逻辑：
 * - CREATE 事件：其他实例创建了新会话，本实例需要同步到本地 activeSessions
 * - REMOVE 事件：其他实例删除了会话，本实例需要清理本地 activeSessions
 *
 * @author xiaofuge bugstack.cn @小傅哥
 */
@Slf4j
@Component
public class SessionRedisListener {

    @Resource
    private IDistributedService distributedRedisService;

    /**
     * 应用启动后自动订阅 Redis Session 同步事件
     * <p>
     * 订阅链路：this → IDistributedService → ISessionManagementService → ISessionDistributedService → ISessionPort → Redis Topic
     */
    @PostConstruct
    public void subscribe() {
        distributedRedisService.subscribeSessionSyncEvent(this::onMessage);
        log.info("已完成 Redis Session 同步订阅");
    }

    /**
     * 接收 Redis Topic 消息并转发到 Case 层处理
     * <p>
     * 此方法仅做日志记录和事件转发，不包含任何业务逻辑。
     * 业务判断（如本地是否已有该会话）由 Case 层和 Domain 层负责。这玩意就跟监听mq消息是类似的。
     *
     * @param event Redis Topic 推送的会话同步事件
     */
    private void onMessage(SessionSyncEventVO event) {
        if (event == null || event.getSessionSyncInfo() == null) {
            return;
        }

        log.info("监听 Redis Session 事件 eventType:{} sessionId:{}",
                event.getEventType(), event.getSessionSyncInfo().getSessionId());
        distributedRedisService.handleSessionSyncEvent(event);
    }

}
