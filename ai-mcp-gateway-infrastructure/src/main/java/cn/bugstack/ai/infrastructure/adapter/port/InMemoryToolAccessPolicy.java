package cn.bugstack.ai.infrastructure.adapter.port;

import cn.bugstack.ai.domain.tool.adapter.port.IToolAccessPolicyPort;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** MVP 授权适配器。默认不额外限制既有 HTTP Tool；可通过显式拒绝集合收紧访问。 */
@Component
public class InMemoryToolAccessPolicy implements IToolAccessPolicyPort {
    private Set<String> denied = ConcurrentHashMap.newKeySet();

    @Override
    public boolean isAllowed(String gatewayId, String toolName) {
        return !denied.contains(key(gatewayId, toolName));
    }

    public void deny(String gatewayId, String toolName) {
        denied.add(key(gatewayId, toolName));
    }

    public void allow(String gatewayId, String toolName) {
        denied.remove(key(gatewayId, toolName));
    }

    private String key(String gatewayId, String toolName) {
        return String.valueOf(gatewayId) + "\u0000" + String.valueOf(toolName);
    }
}
