# mysql-readonly-template-query Specification

## Purpose
让经过发布和审核的 MySQL 查询模板能够通过 MCP Tool 被安全调用，支持电商数仓中包含 JOIN、聚合、子查询和时间参数的只读分析查询，并返回可供 MCP Client 消费的结构化结果。

## Requirements

### Requirement: Gateway SHALL manage a fixed MySQL data source for each template Tool

每个 MySQL 模板 Tool MUST 绑定一个固定的数据源引用和只读执行策略。数据源凭证 MUST 使用安全密钥引用；MCP Client 不得在调用时覆盖数据源信息。

#### Scenario: Template Tool resolves its data source

- **WHEN** 管理员发布绑定 `warehouse-test` 数据源的模板 Tool
- **THEN** Gateway 后续调用该 Tool 只解析 `warehouse-test`，不接受客户端传入的其他数据源

#### Scenario: Missing or disabled data source

- **WHEN** 模板 Tool 引用的数据源不存在或已停用
- **THEN** Gateway 返回 `DATASOURCE_UNAVAILABLE`，并且不执行 SQL

### Requirement: Gateway SHALL execute published templates with bound parameters

Gateway MUST 根据 Tool 版本解析已发布模板，校验参数并使用参数绑定执行 SQL。参数值不得通过字符串拼接注入 SQL；模板可以包含 JOIN、聚合、子查询和合法的 CTE SELECT。

#### Scenario: Join and aggregate template query

- **WHEN** 客户端提供合法的门店、时间范围和订单状态参数
- **THEN** Gateway 绑定参数并通过 JDBC 执行包含 JOIN 和聚合的模板，返回查询结果

#### Scenario: Invalid parameter type

- **WHEN** 客户端将时间参数或数值参数传入不符合 Tool Schema 的类型
- **THEN** Gateway 返回 `INVALID_ARGUMENT`，并且不执行 SQL

### Requirement: Gateway SHALL return a bounded structured result

MySQL 查询结果 MUST 包含列信息、数据行数量和截断状态，并遵守 Tool 或数据源配置的最大行数、最大结果字节数和最大列数。超过上限时 MUST 返回明确的超限错误或 `truncated=true`，不得静默丢弃数据。

#### Scenario: Result within limits

- **WHEN** 查询结果未超过配置的行数和字节限制
- **THEN** Gateway 返回完整的列和行，并将 `truncated` 设置为 `false`

#### Scenario: Result exceeds limits

- **WHEN** 查询结果超过最大结果限制
- **THEN** Gateway 按策略返回 `RESULT_LIMIT_EXCEEDED` 或明确标记 `truncated=true`，并释放 JDBC 资源

### Requirement: Gateway SHALL fail safely on template validation errors

模板在发布或首次执行前 MUST 通过语法、参数占位符和只读策略校验。解析失败、模板不存在、版本不可用或策略不明确时 MUST 拒绝执行，不得降级为直接执行原始 SQL。

#### Scenario: Unparseable template

- **WHEN** 模板 SQL 无法被支持的 SQL Parser 解析
- **THEN** 模板不能发布或执行，并返回 `SQL_SYNTAX_ERROR` 或 `SQL_POLICY_REJECTED`

#### Scenario: Template version is not published

- **WHEN** Tool 指向草稿、已废弃或不存在的模板版本
- **THEN** Gateway 返回模板不可用错误，并且不获取 MySQL 连接

### Requirement: Gateway SHALL support controlled connection and query lifecycle

Gateway MUST 使用独立连接池执行 MySQL 查询，配置连接超时、获取连接超时和查询超时，并在成功、失败、取消和超时路径释放 Statement、ResultSet 和 Connection。

#### Scenario: Query timeout releases resources

- **WHEN** 查询超过配置的执行时间
- **THEN** Gateway 取消或终止查询，返回 `QUERY_TIMEOUT`，并确保连接归还连接池
