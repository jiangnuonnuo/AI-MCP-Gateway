package cn.bugstack.ai.domain.session.adapter.port;

import cn.bugstack.ai.domain.session.model.valobj.SessionSyncEventVO;
import cn.bugstack.ai.domain.session.model.valobj.SessionSyncInfoVO;
import cn.bugstack.ai.domain.session.model.valobj.gateway.McpToolProtocolConfigVO;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

/**
 * 回话端口
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/1/30 20:55
 */
public interface ISessionPort {

    Object toolCall(McpToolProtocolConfigVO.HTTPConfig httpConfig, Object params) throws IOException;

    /**
     * 保存活跃会话元数据到 Redis
     */
    void saveSessionSyncInfo(SessionSyncInfoVO sessionSyncInfoVO);

    /**
     * 删除活跃会话元数据
     */
    void removeSessionSyncInfo(String sessionId);

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

}
