package cn.bugstack.ai.infrastructure.config;

import cn.bugstack.ai.domain.mysql.adapter.port.IMysqlDataSourceRegistry;
import cn.bugstack.ai.domain.mysql.adapter.port.IMysqlQueryPort;
import cn.bugstack.ai.domain.mysql.adapter.port.ISqlSafetyPort;
import cn.bugstack.ai.infrastructure.adapter.port.MysqlJdbcGateway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 组装目标数仓的独立 JDBC 执行链，不复用 Gateway 控制库的数据源 Bean。 */
@Configuration
public class MysqlExecutionConfig {

    @Bean(destroyMethod = "close")
    public MysqlJdbcGateway mysqlJdbcGateway(IMysqlDataSourceRegistry registry,
                                             ISqlSafetyPort safetyPort) {
        return new MysqlJdbcGateway(registry, safetyPort);
    }

    @Bean
    public IMysqlQueryPort mysqlQueryPort(MysqlJdbcGateway gateway) {
        return gateway;
    }
}
