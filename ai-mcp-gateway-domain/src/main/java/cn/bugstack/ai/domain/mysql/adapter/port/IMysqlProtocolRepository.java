package cn.bugstack.ai.domain.mysql.adapter.port;

import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplate;

import java.util.Optional;

/** mcp_protocol_mysql 的持久化端口；不提供全局模板枚举或内存兜底。 */
public interface IMysqlProtocolRepository {

    Optional<MysqlTemplate> find(String protocolRef, String version);

    void save(MysqlTemplate protocol);

    void delete(String protocolRef, String version);
}
