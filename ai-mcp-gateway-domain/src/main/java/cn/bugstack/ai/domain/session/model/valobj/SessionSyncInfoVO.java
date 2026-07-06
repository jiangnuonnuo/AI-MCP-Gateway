package cn.bugstack.ai.domain.session.model.valobj;

import cn.bugstack.ai.domain.session.model.valobj.enums.SessionTransportTypeEnumVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 分布式会话同步信息
 *
 * @author xiaofuge bugstack.cn @小傅哥
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SessionSyncInfoVO {

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 网关ID
     */
    private String gatewayId;

    /**
     * API Key
     */
    private String apiKey;

    /**
     * 传输协议
     */
    private SessionTransportTypeEnumVO transportType;

    /**
     * 创建时间（毫秒时间戳）
     */
    private long createTime;

    /**
     * 最后访问时间（毫秒时间戳）
     */
    private long lastAccessedTime;

    /**
     * 节点标识，标记该 Session 由哪个节点创建
     * <p>
     * 用于分布式清理时，每个节点只清理自己创建的 Session，
     * 避免误删其他节点上仍在活跃的 Session。
     */
    private String nodeId;

    /**
     * 是否活跃
     */
    private boolean active;

}
