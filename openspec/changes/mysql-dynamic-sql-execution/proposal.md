## Why

现有 MySQL Gateway 只能执行服务端保存的 SQL 模板，外部调用方无法通过正式 MCP `tools/call` 传入一条待执行的只读 SQL。下一阶段需要把“外部 SQL 输入 → Gateway 治理 → 真实 JDBC → MCP 结构化结果”打通，供普通 MCP Client 和能够生成 Text-to-SQL 的 Agent 使用。

本变更只覆盖动态只读 SQL 的执行闭环和真实验收。日志增强、审计持久化、Text-to-SQL 生成、schema 元数据和查询成本分析留在后续变更。

## What Changes

- 新增 `MYSQL_DYNAMIC_READONLY` Tool 执行模式，与 `MYSQL_TEMPLATE` 并列。
- 通过现有 Gateway-scoped Tool 绑定固定数据源和查询上限；SQL 正文及命名参数由外部 `tools/call` 请求传入。
- 使用责任链/规则模式统一模板与动态 SQL 的安全校验，并允许两种模式在输入解析、参数绑定、只读策略、资源限制和 JDBC 结果封装上共享规则。
- 动态 SQL 仅允许单条只读 `SELECT`/合法 `WITH`，禁止写入、DDL、事务、锁、文件输出、危险函数和未绑定参数。
- 动态 Tool 的 `tools/list` Schema 暴露 `sql` 与 `parameters`，不暴露数据源、凭证或服务端策略；动态参数首版仅支持命名参数。
- 让动态 Tool 通过正式 MCP SSE/Streamable `tools/call` 执行真实 MySQL 查询，返回现有结构化 `MysqlQueryResult` 语义。
- 保留模板 Tool、HTTP Tool 和既有 MCP 发现/调用兼容性。
- 补充动态 SQL 的控制面执行模式字段和兼容迁移任务；不新建重复的 MySQL 模板表。
- 增加真实 MySQL、MCP 协议和失败关闭验收；本变更不扩展日志字段、日志存储或审计查询能力。

## Capabilities

### New Capabilities

- `mysql-dynamic-readonly-query`: 外部传入只读 SQL 和命名参数，经 Gateway 治理后执行并返回有界结构化结果。

### Modified Capabilities

- `mysql-query-governance`: 将参数绑定、只读策略、资源限制和失败关闭规则扩展到动态 SQL 输入。
- `multi-backend-tool-execution`: 增加动态 MySQL Tool 执行模式，并要求按 Tool 绑定路由到动态执行器。

## Impact

- **Domain**：增加动态执行模式、SQL 执行输入模型、模式选择规则和共享安全责任链；模板与动态请求都转换为统一查询命令。
- **Infrastructure**：增加动态 SQL Tool 执行器，扩展参数绑定和 JDBC 查询端口以接受外部 SQL；复用现有连接池、结果限制和异常映射。
- **Session/Tool**：动态 Tool Schema 生成 `sql` 与开放的命名参数对象；动态调用绕过模板参数映射，但仍经过 Tool 状态和权限校验。
- **Control plane**：在 `mcp_protocol_mysql` 增加执行模式语义，模板记录保持原行为，动态记录不依赖服务端 SQL 正文。字段可空性、默认值和迁移需在实施前确认。
- **测试与验收**：新增 Domain/Infrastructure/Trigger/MCP 端到端测试，验证成功、绑定、策略拒绝、参数错误、资源限制、协议调用和模板回归。
- **明确不影响**：不实现 SQL 生成模型、不修改 Agent 编排、不增加日志/审计持久化、不新增数据源类型。
