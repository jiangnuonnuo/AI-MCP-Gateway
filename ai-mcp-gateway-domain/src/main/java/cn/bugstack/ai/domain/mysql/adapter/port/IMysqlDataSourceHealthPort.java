package cn.bugstack.ai.domain.mysql.adapter.port;

/**
 * MySQL 数据源连接健康检查端口。
 *
 * <p>领域层只关心指定数据源能否建立有效连接，JDBC、连接池和凭证解密由 Infrastructure 实现。</p>
 */
@FunctionalInterface
public interface IMysqlDataSourceHealthPort {

    boolean isHealthy(String datasourceRef);

}
