## Purpose

为 Gateway 的 MySQL 执行建立不可绕过的只读安全和资源治理边界，确保任何写入、DDL、事务、锁定、文件输出或其他副作用语句在数据库执行前被拒绝，并使查询行为可授权、可审计、可限制。

## ADDED Requirements

### Requirement: MySQL execution SHALL be read-only by policy and database privilege

MVP 的 MySQL 执行 MUST 使用只读数据库账号和只读 Tool 策略。Gateway MUST 在执行前拒绝写操作、DDL、DCL、事务控制、锁定、文件输出、存储过程和其他已识别的副作用操作；数据库权限 MUST 作为第二道防线。

#### Scenario: Write statement is rejected before execution

- **WHEN** 模板或请求包含 `INSERT`、`UPDATE`、`DELETE`、`REPLACE` 或其他写操作
- **THEN** Gateway 返回 `SQL_POLICY_REJECTED`，并且不提交该语句到 MySQL

#### Scenario: DDL, lock, or file output is rejected

- **WHEN** SQL 包含 `CREATE`、`ALTER`、`DROP`、`TRUNCATE`、`FOR UPDATE`、`LOCK TABLES`、`INTO OUTFILE` 或等价危险语法
- **THEN** Gateway 拒绝执行并记录策略拒绝审计

### Requirement: SQL policy SHALL parse the complete statement

SQL 安全校验 MUST 解析完整语句树，拒绝多语句、解析失败和不支持的语法。安全判断不得仅依赖字符串前缀、大小写匹配或正则表达式；解析失败 MUST 采用失败关闭策略。

#### Scenario: Multiple statements are rejected

- **WHEN** 请求包含以分号分隔的多个 SQL 语句
- **THEN** Gateway 返回 `SQL_POLICY_REJECTED`，并且不执行任何语句

#### Scenario: Parser cannot determine statement type

- **WHEN** SQL Parser 无法识别完整 SQL
- **THEN** Gateway 拒绝执行并返回安全策略错误

### Requirement: SQL policy SHALL enforce parameter binding

MySQL Tool MUST 区分 SQL 文本和参数值。模板参数和动态查询参数 MUST 使用 PreparedStatement 或等价绑定机制；Gateway MUST 拒绝通过参数直接拼接 SQL 的执行路径。

#### Scenario: Parameterized query executes

- **WHEN** 查询使用命名或位置占位符并提供合法参数
- **THEN** Gateway 完成参数绑定后执行查询

#### Scenario: Unbound or extra parameter

- **WHEN** 参数缺失、重复、类型不匹配或存在未声明参数
- **THEN** Gateway 返回 `SQL_PARAMETER_ERROR`，并且不执行查询

### Requirement: Query governance SHALL enforce resource limits

每个数据源、Tool 和调用方 MUST 受最大 SQL 长度、执行时间、返回行数、结果字节数、并发数和调用频率限制。客户端只能请求不超过服务端上限的更小限制。

#### Scenario: Client requests excessive limit

- **WHEN** 客户端请求的 `maxRows` 或超时时间高于服务端策略
- **THEN** Gateway 使用服务端上限或拒绝请求，不得放宽策略

#### Scenario: Concurrent query limit is reached

- **WHEN** 数据源或调用方已达到并发查询上限
- **THEN** Gateway 返回资源受限错误，并且不创建新的 JDBC 执行任务

### Requirement: Access decisions SHALL be explicit and auditable

Gateway MUST 在 Tool、数据源和查询模式层面执行权限判断。动态 SQL 能力在 MVP 中 MUST 默认不可用；任何策略拒绝、权限拒绝、数据源停用和查询超限都 MUST 产生可关联的审计记录。

#### Scenario: Dynamic SQL is disabled in MVP

- **WHEN** 客户端调用未被明确启用的动态 SQL Tool
- **THEN** Gateway 返回 `ACCESS_DENIED` 或 `TOOL_NOT_FOUND`，并且不解析或执行 SQL

#### Scenario: Authorized template call is auditable

- **WHEN** 有权限的调用方执行模板查询
- **THEN** 审计记录包含调用方、Tool、数据源、模板版本、策略决策、耗时、行数和错误码

### Requirement: Sensitive information SHALL not leave the gateway boundary

日志、MCP 错误和审计输出 MUST 不包含数据库密码、密钥、Token、Authorization Header、完整敏感结果或可恢复的敏感参数值。SQL 审计应优先记录模板版本或规范化 SQL 摘要。

#### Scenario: Database authentication fails

- **WHEN** MySQL 返回认证失败或连接错误
- **THEN** MCP 响应只返回稳定的 `DATASOURCE_UNAVAILABLE` 或执行错误，不返回密码、JDBC URL 或数据库堆栈
