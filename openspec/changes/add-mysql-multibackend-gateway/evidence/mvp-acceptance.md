# MVP 链路验收记录

## 已通过场景

- `data_warehouse` 连接通过运行时凭证注入，JDBC 只读模板查询成功。
- `fact_order` JOIN `dim_channel` 的时间范围聚合模板返回列、行、行数、截断状态和 queryId。
- `tools/list` 暴露模板名称、描述和参数 Schema，不返回 SQL 正文、数据源地址或凭证。
- `tools/call` 通过 `ToolExecutorRouter` 选择 MySQL 模板执行器；HTTP 执行器保持 GET/POST 和多参数兼容。
- SQL Parser/责任链拒绝写操作、DDL、DCL、事务、锁定、文件输出、CALL、危险函数、多语句和解析失败。
- 缺失、额外和类型错误参数在 JDBC 前返回 `SQL_PARAMETER_ERROR`；资源限制和异常映射为稳定错误码。
- 连接池健康检查、并发限制、查询超时、最大行数、最大列数、结果字节数和资源释放均由执行器治理。
- 审计摘要包含 gatewayId、toolName、后端、requestId、策略结果、耗时、行数、结果字节数和错误码，不保存参数值或凭证。
- `MysqlMcpRealIntegrationTest` 使用运行时凭证执行真实的 `tools/list -> tools/call -> JDBC -> MCP` 纵向链路并通过。
- 只读测试账号的 `SHOW GRANTS` 和数据库层写操作拒绝验证通过，应用层 SQL 安全责任链同步拒绝同一写操作。

验证命令：

```text
docker run --rm -v "$PWD":/workspace -v "$HOME/.m2":/root/.m2 \
  -w /workspace maven:3.9.11-eclipse-temurin-17 mvn -q test
```

本机数仓集成测试只有在显式注入 `WAREHOUSE_MYSQL_USERNAME` 和 `WAREHOUSE_MYSQL_PASSWORD` 时启用；未注入时默认跳过，不影响全量测试。

真实 MySQL 调用和权限双重防线的具体命令与结果见 [real-mysql-call.md](real-mysql-call.md)。

## 未纳入能力

- 动态只读 SQL、异步查询、游标分页、结果文件导出和取消 API。
- 字段级脱敏、行级业务权限、成本预估和数据字典。
- 数据源、模板、策略和 Tool 绑定的正式控制库表、DAO、PO、Mapper、迁移脚本和管理 API。
- 数据库只读账号的生产密钥托管、轮换和权限审计。

## 下一变更范围

先由 Xerina 确认控制面持久化的业务含义、字段结构、密钥引用、兼容策略和迁移回滚方案，再单独设计控制库持久化变更。
