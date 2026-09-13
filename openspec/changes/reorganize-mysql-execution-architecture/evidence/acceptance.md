# 重构验收记录

## 最终职责归属

| 层 | 归属内容 |
| --- | --- |
| App Config | `MysqlConnectionProperties`、`MysqlDataSourceConfig`、连接池与超时上限、密钥引用、MVP 开关、MVP 模板、Bean 组合根和密钥查找函数 |
| Domain | `MysqlDataSourceRef`、模板与数据源状态、查询策略、`MysqlTemplateQueryService`、生命周期服务、SQL 安全责任链和逻辑结果模型 |
| Infrastructure | `adapter/port` 中的 `MysqlJdbcGateway`、`MysqlSqlParser`、`MysqlTemplateExecutor` 等能力适配器；`adapter/repository` 中的内存 Registry；`mysql` 参数绑定、`MysqlSecretResolver` 密钥适配、逻辑类型映射和 `observability` 执行指标；异常契约统一由 `types.exception` 提供 |
| 既有 Tool 链路 | `ToolsCallHandler → ToolExecutorRouter → ToolExecutor` 保持不变，`MysqlTemplateExecutor` 仅作为 MySQL 策略适配器接入 |

## 边界检查

- Domain 未引用 JDBC、Hikari、JSqlParser、Environment、System 环境 API 或 Infrastructure 实现。
- Infrastructure 未引用 App Config、System 环境 API 或 Spring Environment。
- Infrastructure Config 下不再保留 MySQL 启动配置和 MVP 初始化类。
- `MysqlDomainException`、`MysqlQueryException` 和 `MysqlParameterException` 均位于 `ai-mcp-gateway-types` 的 `types.exception`；Infrastructure 不再定义独立异常包。
- 未保留重复的 `MysqlQueryAdapter`、`MysqlAstSqlParser` 或第二套路由/Handler。
- 测试源统一位于 `ai-mcp-gateway-app/src/test/java/cn/bugstack/ai/test`。

## `adapter/port` 保留项审查

| 类 | 保留原因 | 是否仓储/技术辅助 |
| --- | --- | --- |
| `MysqlJdbcGateway` | 实现 `IMysqlQueryPort`，连接池和 JDBC 生命周期是独立外部能力边界 | 外部能力 Port |
| `MysqlSqlParser` | 实现 `ISqlAnalysisPort`，JSqlParser 可替换且 Domain 只接收逻辑分析事实 | 外部能力 Port |
| `MysqlTemplateExecutor` | 实现既有 `ToolExecutor`，把 MySQL 接入原有 Tool 路由 | Tool 策略适配 |
| `HttpToolExecutor`、`SessionPort` | 既有 HTTP/Redis 外部能力适配，不属于本次 MySQL 重复设计 | 外部能力 Port |
| `InMemoryToolAccessPolicy`、`InMemoryToolExecutionAudit` | 实现 Tool 领域端口，拥有独立策略/审计状态 | 外部能力 Port |

模板和数据源的内存 Registry 已移入 `adapter/repository`；`MysqlTemplateParameterBinder`、
`MysqlExecutionMetrics`、`MysqlQueryException` 分别移入 `mysql`、`observability` 和统一的
`types.exception`，参数绑定错误类型也归入 `types.exception`。因此剩余 `adapter/port` 类均有独立 Domain Port 或既有 Tool 契约，
没有为 JDBC 私有步骤额外创建公开操作类。

## 验证命令

```text
docker run --rm -v "$PWD":/workspace -w /workspace \
  maven:3.9.11-eclipse-temurin-17 mvn -q -pl ai-mcp-gateway-app -am test
git diff --check
codegraph links --root . --include-glob 'docs/architecture/*.md' --json
openspec validate reorganize-mysql-execution-architecture --json
```

全量 Maven 测试、Markdown 链接检查、OpenSpec 校验和 UTF-8 文件检查通过；集成测试在未提供 `WAREHOUSE_MYSQL_USERNAME/PASSWORD` 时按约定跳过，不会建立目标数仓连接。
