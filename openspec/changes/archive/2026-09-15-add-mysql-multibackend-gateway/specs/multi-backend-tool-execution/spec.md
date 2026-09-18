## Purpose

为 MCP Client 提供统一的 Tool 发现和调用入口，并允许同一个 Gateway 按工具配置选择 HTTP 或 MySQL/JDBC 后端执行器，同时保持现有 HTTP Tool 的调用兼容性。

## ADDED Requirements

### Requirement: Tool SHALL declare a supported backend execution type

每个已发布的 MCP Tool MUST 绑定一个受支持的后端执行类型和执行模式。MVP 至少支持 HTTP 请求和 MySQL 只读模板查询；Tool 配置不得要求 MCP Client 传入后端地址、数据库地址或凭证。

#### Scenario: MySQL template Tool is listed

- **WHEN** 客户端调用 `tools/list` 且 MySQL 模板 Tool 已启用并且调用方有权限
- **THEN** Gateway 返回该 Tool 的名称、描述和输入 Schema，并隐藏数据源凭证及 SQL 正文

#### Scenario: Client cannot select a backend target

- **WHEN** 客户端在 `tools/call` 参数中传入数据源 ID、JDBC URL 或目标后端地址
- **THEN** Gateway MUST 忽略或拒绝这些未由 Tool 配置声明的字段，并不得使用它们建立连接

### Requirement: Gateway SHALL route Tool calls to the configured executor

Gateway MUST 根据已解析的 Tool 后端类型选择对应执行器。HTTP Tool MUST 继续走现有 HTTP 执行路径；MySQL Tool MUST 走 MySQL/JDBC 执行路径，不能将 MySQL 配置伪装为 HTTP URL。

#### Scenario: Existing HTTP Tool remains callable

- **WHEN** 客户端调用已存在的 HTTP Tool
- **THEN** Gateway 使用 HTTP 执行器完成请求，并保持既有 MCP 工具名称、参数 Schema 和响应兼容

#### Scenario: MySQL Tool uses JDBC

- **WHEN** 客户端调用绑定 MySQL 数据源的模板 Tool
- **THEN** Gateway 选择 MySQL 执行器并通过 JDBC 执行查询，不发起 HTTP 请求到 MySQL

### Requirement: Gateway SHALL validate the MCP request before backend execution

Gateway MUST 在建立后端连接或发起后端请求前校验 Tool 状态、调用权限、参数类型、必填参数和请求大小。校验失败 MUST 返回稳定的 MCP 参数或权限错误。

#### Scenario: Missing required template parameter

- **WHEN** 客户端缺少模板声明的必填参数
- **THEN** Gateway 返回 `INVALID_ARGUMENT`，并且不获取 MySQL 连接

#### Scenario: Disabled Tool is called

- **WHEN** 客户端调用已停用的 Tool
- **THEN** Gateway 返回 `TOOL_DISABLED`，并且不执行任何后端操作

### Requirement: Gateway SHALL normalize backend results and errors

Gateway MUST 将后端成功结果转换为 MCP 结构化内容，并将后端异常映射为稳定错误码。响应不得泄露凭证、内部地址、完整数据库异常堆栈或敏感配置。

#### Scenario: Structured MySQL result

- **WHEN** MySQL 查询成功返回列和数据行
- **THEN** MCP 响应包含 `columns`、`rows`、`rowCount`、`truncated` 和可关联的 `queryId`

#### Scenario: Backend timeout

- **WHEN** HTTP 或 MySQL 后端超过 Tool 配置的执行超时
- **THEN** Gateway 返回 `QUERY_TIMEOUT` 或等价稳定错误，并释放后端资源

### Requirement: Gateway SHALL expose execution observability

Gateway MUST 为每次 Tool 调用生成或传递请求标识，并记录 Tool、后端类型、调用方、耗时、结果规模和错误码等元数据。日志不得记录密码、Token、API Key、Authorization 或完整敏感结果。

#### Scenario: Auditable Tool call

- **WHEN** 一次 MySQL Tool 调用完成或被拒绝
- **THEN** 审计记录包含 `gatewayId`、`toolName`、后端类型、调用方、请求 ID、策略结果、耗时和错误码
