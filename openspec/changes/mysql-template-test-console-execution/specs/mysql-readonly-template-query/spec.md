## MODIFIED Requirements

### Requirement: Gateway SHALL execute published templates with bound parameters

Gateway MUST 根据 Tool 版本解析已发布模板，校验参数并使用参数绑定执行 SQL。参数值不得通过字符串拼接注入 SQL；模板可以包含 JOIN、聚合、子查询和合法的 CTE SELECT。管理端模板测试 MUST 复用同一执行规则，只能通过模板引用、版本和参数对象触发，不得以“测试”名义直接执行前端提交的 SQL 或覆盖绑定数据源。

#### Scenario: Join and aggregate template query

- **WHEN** 客户端或管理端测试请求提供合法的门店、时间范围和订单状态参数
- **THEN** Gateway 绑定参数并通过 JDBC 执行包含 JOIN 和聚合的模板，返回查询结果及可关联的 `queryId`

#### Scenario: Invalid parameter type

- **WHEN** 客户端或管理端测试将时间参数或数值参数传入不符合 Tool Schema 的类型
- **THEN** Gateway 返回 `INVALID_ARGUMENT` 或 `SQL_PARAMETER_ERROR`，并且不获取目标数据库连接或执行 SQL

#### Scenario: Admin test cannot override execution semantics

- **WHEN** 管理端测试请求尝试提交新的 SQL、数据源引用或放宽模板策略
- **THEN** Gateway 忽略或拒绝这些字段，并继续使用已发布模板绑定的 SQL、数据源和服务端策略

### Requirement: Gateway SHALL return a bounded structured result

MySQL 查询结果 MUST 包含列信息、数据行数量和截断状态，并遵守 Tool 或数据源配置的最大行数、最大结果字节数和最大列数。超过上限时 MUST 返回明确的超限错误或 `truncated=true`，不得静默丢弃数据。管理端测试响应 MUST 透传该结构化结果和执行元数据，而不是转换为业务表格或图表。

#### Scenario: Result within limits

- **WHEN** 查询结果未超过配置的行数和字节限制
- **THEN** Gateway 返回完整的列和行，将 `truncated` 设置为 `false`，并在管理测试响应中保留相同的 `columns`、`rows` 和 `rowCount`

#### Scenario: Result exceeds limits

- **WHEN** 查询结果超过最大结果限制
- **THEN** Gateway 按策略返回 `RESULT_LIMIT_EXCEEDED` 或明确标记 `truncated=true`，并释放 JDBC 资源；管理测试页面不得显示“成功但无数据”的假状态

### Requirement: Gateway SHALL fail safely on template validation errors

模板在发布或首次执行前 MUST 通过语法、参数占位符和只读策略校验。解析失败、模板不存在、版本不可用或策略不明确时 MUST 拒绝执行，不得降级为直接执行原始 SQL。管理端测试 MUST 使用相同的失败关闭规则，并返回可定位的失败阶段。

#### Scenario: Unparseable template

- **WHEN** 模板 SQL 无法被支持的 SQL Parser 解析
- **THEN** 模板不能发布或执行，并返回 `SQL_SYNTAX_ERROR` 或 `SQL_POLICY_REJECTED`，测试阶段标记为策略/模板校验失败

#### Scenario: Template version is not published

- **WHEN** Tool 或管理端测试指向草稿、已废弃或不存在的模板版本
- **THEN** Gateway 返回模板不可用错误，并且不获取 MySQL 连接

### Requirement: Gateway SHALL support controlled connection and query lifecycle

Gateway MUST 使用独立连接池执行 MySQL 查询，配置连接超时、获取连接超时和查询超时，并在成功、失败、取消和超时路径释放 Statement、ResultSet 和 Connection。管理端测试 MUST 关联同一个 queryId，并将阶段耗时与资源释放结果纳入验收证据。

#### Scenario: Query timeout releases resources

- **WHEN** 查询超过配置的执行时间
- **THEN** Gateway 取消或终止查询，返回 `QUERY_TIMEOUT`，标记执行 SQL 阶段失败，并确保连接归还连接池

#### Scenario: Admin test reports lifecycle metadata

- **WHEN** 管理端测试查询成功或失败
- **THEN** 响应包含 queryId、阶段状态和耗时，且不改变 MCP Tool 正式调用的连接池、超时和释放语义

## ADDED Requirements

### Requirement: Manual Tool tests SHALL share the formal Gateway invocation path

管理端 Tool 手动测试 MUST 从当前 Gateway 已存在且启用的 Tool 绑定解析协议、模板和数据源，并使用与正式 MCP `tools/call` 相同的参数校验、只读策略、执行器路由、资源治理和结构化结果语义。测试请求不得覆盖 SQL、数据源、行数、超时或策略。

#### Scenario: Manual Tool call matches formal Tool execution

- **WHEN** 管理员使用同一 Tool 和同一组合法参数运行手动测试，且 MCP Client 使用该 Tool 发起正式 `tools/call`
- **THEN** 两条链路使用同一绑定和执行规则，返回的 `columns`、`rows`、`rowCount`、`truncated` 和错误治理语义一致

#### Scenario: Manual Tool call cannot bypass read-only rules

- **WHEN** 手动测试请求包含危险参数、尝试放宽策略或指向停用数据源
- **THEN** Gateway 在获取连接前拒绝请求，返回稳定错误码和失败阶段，不执行写入或危险 SQL

#### Scenario: Tool binding disappears before execution

- **WHEN** 已选择 Tool 在执行前解绑、停用或不再属于当前 Gateway
- **THEN** 测试返回 Tool 不可用错误，不使用旧缓存配置继续执行，不创建新的持久化绑定
