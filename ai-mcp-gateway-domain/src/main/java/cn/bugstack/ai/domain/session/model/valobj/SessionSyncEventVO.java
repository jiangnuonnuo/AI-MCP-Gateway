package cn.bugstack.ai.domain.session.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 会话同步事件
 *
 * @author xiaofuge bugstack.cn @小傅哥
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SessionSyncEventVO {

    public enum EventType {
        CREATE,
        REMOVE
    }

    /**
     * 事件类型
     */
    private EventType eventType;

    /**
     * 会话同步信息
     */
    private SessionSyncInfoVO sessionSyncInfo;

}
