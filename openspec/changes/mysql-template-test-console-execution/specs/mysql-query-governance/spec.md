## MODIFIED Requirements

### Requirement: SQL policy SHALL enforce parameter binding

MySQL Tool 和管理端模板测试 MUST 区分 SQL 文本和参数值。模板参数和动态查询参数 MUST 使用 PreparedStatement 或等价绑定机制；Gateway MUST 拒绝通过参数直接拼接 SQL 的执行路径。管理端请求只允许提交参数对象，不能提交待执行 SQL 文本。

#### Scenario: Parameterized query executes

- **WHEN** MCP Client 或管理员使用命名/位置占位符模板并提供合法参数
- **THEN** Gateway 完成参数绑定后执行查询，并可在内部审计中关联规范化模板摘要和 queryId

#### Scenario: Unbound or extra parameter

- **WHEN** 参数缺失、重复、类型不匹配或存在未声明参数
- **THEN** Gateway 返回 `SQL_PARAMETER_ERROR`，并且不执行查询

#### Scenario: SQL injection text is never assembled

- **WHEN** 参数值包含引号、分号、注释符或其他 SQL 片段
- **THEN** Gateway 将其视为参数数据进行类型/长度校验，不拼接进 SQL 文本；不满足契约时拒绝执行

### Requirement: Query governance SHALL enforce resource limits

每个数据源、Tool、管理端测试请求和调用方 MUST 受最大 SQL 长度、执行时间、返回行数、结果字节数、并发数和调用频率限制。客户端和管理端只能请求不超过服务端上限的更小限制；管理测试 UI 展示的护栏必须来源于服务端返回的有效策略，而不是前端常量冒充执行结果。

#### Scenario: Client requests excessive limit

- **WHEN** 客户端或管理端测试请求的 `maxRows` 或超时时间高于服务端策略
- **THEN** Gateway 使用服务端上限或拒绝请求，不得放宽策略，并将最终生效限制返回给测试结果

#### Scenario: Concurrent query limit is reached

- **WHEN** 数据源或调用方已达到并发查询上限
- **THEN** Gateway 返回资源受限错误，并且不创建新的 JDBC 执行任务；管理端显示可重试的资源限制状态

### Requirement: Access decisions SHALL be explicit and auditable

Gateway MUST 在 Tool、数据源和查询模式层面执行权限判断。动态 SQL 能力在 MVP 中 MUST 默认不可用；任何策略拒绝、权限拒绝、数据源停用和查询超限都 MUST 产生可关联的审计记录。管理端测试与正式 MCP Tool 调用 MUST 使用相同的授权、模板版本、数据源和只读策略判断，只增加管理端可见的阶段结果。

#### Scenario: Dynamic SQL is disabled in MVP

- **WHEN** 客户端或管理端调用未被明确启用的动态 SQL Tool
- **THEN** Gateway 返回 `ACCESS_DENIED` 或 `TOOL_NOT_FOUND`，并且不解析或执行动态 SQL

#### Scenario: Authorized template call is auditable

- **WHEN** 有权限的调用方或管理员执行模板查询
- **THEN** 审计记录包含调用方、Tool/模板、数据源、模板版本、策略决策、queryId、耗时、行数和错误码，但不包含完整敏感参数值

### Requirement: Sensitive information SHALL not leave the gateway boundary

日志、MCP 错误、管理测试响应和审计输出 MUST 不包含数据库密码、密钥、Token、Authorization Header、完整敏感结果或可恢复的敏感参数值。SQL 审计应优先记录模板版本或规范化 SQL 摘要；管理端请求/响应 JSON 对敏感参数必须按参数契约标记脱敏或禁止回显。

#### Scenario: Database authentication fails

- **WHEN** MySQL 返回认证失败或连接错误
- **THEN** MCP 响应和管理测试响应只返回稳定的 `DATASOURCE_UNAVAILABLE` 或执行错误，不返回密码、JDBC URL 或数据库堆栈，并将获取数据源连接阶段标记失败

#### Scenario: Sensitive parameter is displayed safely

- **WHEN** 模板包含被标记为敏感的参数且管理员查看请求 JSON、SQL 绑定或执行日志
- **THEN** 页面和接口仅显示掩码/摘要，不返回可恢复的原始值
