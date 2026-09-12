package cn.bugstack.ai.domain.tool.adapter.port;

/** Tool 级访问授权端口；未通过授权时不得解析模板或获取 JDBC 连接。 */
public interface IToolAccessPolicyPort {
    boolean isAllowed(String gatewayId, String toolName);
}
