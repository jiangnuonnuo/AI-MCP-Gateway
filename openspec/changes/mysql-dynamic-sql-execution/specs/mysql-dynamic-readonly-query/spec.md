## Purpose

让 MCP Client 或能够生成 Text-to-SQL 的 Agent 将外部 SQL 和命名参数传入 Gateway，由 Gateway 在固定数据源和只读策略下完成真实 MySQL 查询，并返回可验证的结构化结果。

## ADDED Requirements

### Requirement: Gateway SHALL expose an explicitly enabled dynamic read-only MySQL Tool

动态 MySQL Tool MUST 通过 Gateway-scoped Tool 绑定显式启用，并绑定固定数据源和服务端查询策略。未显式启用动态能力的 MySQL Tool MUST 继续按模板模式执行或拒绝动态请求。

#### Scenario: Dynamic Tool is listed with an external SQL contract

- **WHEN** 已启用的 Gateway 包含动态只读 MySQL Tool 且调用方有权限执行该 Tool
- **THEN** `tools/list` 返回顶层必填字段 `sql` 和 `parameters`，`parameters` 是允许命名键的对象，并隐藏数据源、凭证和服务端策略

#### Scenario: Dynamic Tool is disabled by default

- **WHEN** 调用方请求未显式启用动态能力的 Tool 并传入 `sql`
- **THEN** Gateway 返回稳定的权限或参数错误，不解析、不连接数据库且不执行 SQL

### Requirement: Gateway SHALL execute externally supplied SQL with fixed server configuration

动态 Tool MUST 从 `tools/call` 参数读取 SQL 文本和命名参数；数据源、只读策略和资源上限 MUST 由服务端绑定解析，客户端不得覆盖。

#### Scenario: External SQL executes through the official MCP path

- **WHEN** 调用方通过 SSE 或 Streamable MCP `tools/call` 提交合法单条 `SELECT` 和合法命名参数
- **THEN** Gateway 通过 MySQL JDBC 执行真实查询，并返回 `columns`、`rows`、`rowCount`、`truncated` 和 `queryId`

#### Scenario: Client cannot choose the backend

- **WHEN** 动态请求同时提交 `datasourceRef`、JDBC URL、凭证或后端地址
- **THEN** Gateway 拒绝或忽略这些字段，并始终使用 Tool 绑定的数据源

### Requirement: Control plane SHALL manage dynamic bindings without SQL templates

控制面 MUST 为动态只读 Tool 提供独立绑定入口。动态绑定请求 MUST 只声明 Gateway、Tool 身份、固定数据源和服务端资源护栏，不要求 SQL 模板或协议 ID；保存时 MUST 创建 `DYNAMIC_READONLY` 协议记录并建立 Gateway Tool 关系。

#### Scenario: Dynamic binding is created without a template

- **WHEN** 管理端提交 Gateway、Tool 名称、已启用数据源和资源上限，且未提交模板 ID
- **THEN** 控制面创建动态协议和 Tool 绑定，初始状态为停用，返回动态模式及数据源摘要

#### Scenario: Dynamic binding is enabled only with an available datasource

- **WHEN** 管理端启用动态绑定
- **THEN** Gateway 校验固定数据源处于启用状态并同步动态协议状态；数据源停用时返回稳定资源不可用错误

### Requirement: Dynamic SQL SHALL use the shared query governance contract

动态 SQL MUST 与模板 SQL 经过相同的只读安全、参数绑定、资源限制、错误映射和 JDBC 资源释放语义；动态 SQL 不得创建第二套绕过既有治理的执行路径。

#### Scenario: Dynamic SQL is rejected before JDBC execution

- **WHEN** 动态 SQL 包含写操作、DDL、事务、锁、文件输出、危险函数、多语句或无法解析的语法
- **THEN** Gateway 返回 `SQL_POLICY_REJECTED` 或 `SQL_SYNTAX_ERROR`，且不获取连接、不创建 JDBC 执行任务

#### Scenario: Dynamic parameter error stops execution

- **WHEN** 动态 SQL 存在缺失、重复、多余或类型不合法的命名参数
- **THEN** Gateway 返回 `SQL_PARAMETER_ERROR`，且不执行 SQL

### Requirement: Dynamic execution SHALL preserve existing result and failure semantics

动态查询结果 MUST 遵守服务端行数、列数、结果字节数、超时和并发上限，并使用现有 MCP 稳定错误码。动态能力的增加不得改变模板 Tool 和 HTTP Tool 的外部行为。

#### Scenario: Dynamic query exceeds a server limit

- **WHEN** 动态查询超过服务端结果、超时或并发上限
- **THEN** Gateway 返回既有资源错误或明确的 `truncated` 结果，并释放 Connection、Statement 和 ResultSet

#### Scenario: Existing template Tool remains compatible

- **WHEN** 调用方调用已发布的模板 Tool
- **THEN** Gateway 继续使用模板参数契约和固定 SQL 执行，不要求调用方提交 `sql` 字段
