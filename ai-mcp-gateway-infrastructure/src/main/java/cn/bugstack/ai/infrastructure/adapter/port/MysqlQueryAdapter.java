package cn.bugstack.ai.infrastructure.adapter.port;

import cn.bugstack.ai.domain.mysql.adapter.port.IMysqlQueryPort;
import cn.bugstack.ai.domain.mysql.model.command.MysqlQueryCommand;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlQueryResult;
import cn.bugstack.ai.infrastructure.adapter.port.MysqlJdbcGateway;

/** Domain MySQL Query Port 的基础设施适配器。 */
public final class MysqlQueryAdapter implements IMysqlQueryPort, AutoCloseable {
    private final MysqlJdbcGateway gateway;

    public MysqlQueryAdapter(MysqlJdbcGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public MysqlQueryResult execute(MysqlQueryCommand command) {
        return gateway.execute(command);
    }

    @Override
    public void close() {
        gateway.close();
    }
}
