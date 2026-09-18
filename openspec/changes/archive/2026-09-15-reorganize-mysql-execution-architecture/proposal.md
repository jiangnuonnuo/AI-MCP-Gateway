## Why

当前 MySQL 能力已经接入现有 Tool 执行链，但连接池参数、启动属性、领域规则和 JDBC 执行仍然混在 Domain、Infrastructure 与 Infrastructure `config` 中。此次重构只整理 MySQL 能力的职责边界和配置归属，复用现有 Tool 转换、Tool 路由和 MCP 调用链，不重新设计已经稳定的通用执行流程。

## What Changes

- 保持现有 `ToolsCallHandler → ToolExecutorRouter → ToolExecutor` 链路不变，只增加或调整 MySQL Tool 执行策略作为现有策略集合中的一种实现。
- 将业务流程、业务规则、状态转换、策略选择、领域内循环和 SQL 安全责任链统一归入 Domain。
- 将 `MysqlConnectionProperties`、`MysqlDataSourceConfig`、连接池参数、连接地址、密钥引用和启动开关统一归入 `ai-mcp-gateway-app/src/main/java/cn/bugstack/ai/config`。
- 将 MySQL 查询编排从技术适配器中提取为 Domain 服务；MySQL Tool 执行策略只负责把现有 Tool 上下文转换为领域命令并转换结果。
- 将 SQL Parser、PreparedStatement 参数绑定、JDBC 连接池、ResultSet 映射和技术异常处理保留在 Infrastructure。
- 将模板/数据源 Registry 限制为存取适配器，领域状态转换和业务校验由 Domain 完成。
- 将分层、配置归属、责任链、策略模式、IoC 注入和过度拆分规则写成适用于所有后端的通用架构文档，MySQL 只作为示例。
- 保持现有 HTTP Tool、MySQL 只读策略、MCP 结果和错误契约，不新增对外能力，不修改控制库 DDL。

## Capabilities

### New Capabilities

本变更为纯架构重构和规范调整，不新增对外行为，因此不创建行为规格。

### Modified Capabilities

无。`add-mysql-multibackend-gateway` 已定义的 Tool、HTTP、MySQL 只读和结果契约保持不变。

## Impact

- `docs/architecture/README.md` 与 `docs/architecture/design-patterns.md`：补充通用分层和责任链规范，不写成 MySQL 专属规则。
- `ai-mcp-gateway-app/src/main/java/cn/bugstack/ai/config`：承载 MySQL 启动属性、连接池参数、数据源配置和 Bean 组装。
- `ai-mcp-gateway-domain`：承载 MySQL 业务模型、查询编排、参数业务规则和 SQL 安全责任链；不依赖 JDBC 技术类型。
- `ai-mcp-gateway-infrastructure`：承载 MySQL JDBC、Parser、参数绑定、结果映射、资源治理和 Registry 存取适配。
- 现有 Tool 转换、路由和 MCP Handler：保持调用链和接口不变，只增加 MySQL 策略实现的接入验证。
- 测试：继续统一放在 `ai-mcp-gateway-app/src/test/java/cn/bugstack/ai/test`，增加层间依赖、责任链顺序、配置装配和策略兼容测试。
- 不涉及控制库或数仓 DDL、DAO、PO、Mapper 变更。
