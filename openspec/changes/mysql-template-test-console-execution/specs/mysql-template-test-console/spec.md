## Purpose

为管理员提供一个真实的 MySQL 模板测试工作区：从模板契约生成参数输入，执行 Gateway 的只读查询链路，并以阶段状态和原始 MCP JSON 验证请求绑定与响应结果。

## ADDED Requirements

### Requirement: Admins SHALL execute a template test with explicit parameter values

管理端测试接口 MUST 接收模板标识、版本和参数对象，并只允许针对已启用的 MySQL 模板执行。参数对象 MUST 与模板声明的参数契约进行名称、必填性和类型校验；请求不得携带或覆盖 SQL、数据源、模板策略或 Tool 绑定之外的执行语义。

#### Scenario: Test request binds declared parameters

- **WHEN** 管理员提交启用模板 `protocol-900002`、版本 `1.0.0` 以及 `fromTime`、`toTime` 等契约内参数
- **THEN** Gateway 使用这些值进入同一只读模板查询链路，并返回一个可关联的 `queryId`

#### Scenario: Missing or undeclared parameter is rejected

- **WHEN** 测试请求缺少必填参数、包含未声明参数或参数类型不符合模板契约
- **THEN** 接口返回 `SQL_PARAMETER_ERROR` 或 `INVALID_ARGUMENT`，不获取目标数据库连接，也不执行 SQL

### Requirement: The test response SHALL expose a stable execution contract

成功响应 MUST 至少包含模板标识、版本、数据源引用（脱敏）、`queryId`、请求参数安全快照、执行阶段列表、总耗时、返回行数、结果字节数、`truncated` 标记和原始结构化响应。原始响应 MUST 保留 `columns`、`rows`、`rowCount`、`truncated` 和 `queryId` 等 MCP 查询结果语义，不转换成业务报表结构。

#### Scenario: Successful test returns raw structured data

- **WHEN** 目标数据源可用且查询在服务端资源上限内完成
- **THEN** 管理端收到执行成功状态、阶段耗时和与 MCP Tool 一致的结构化 `columns`/`rows` 数据

#### Scenario: Result is bounded and explicitly marked

- **WHEN** 查询结果达到行数、列数或字节上限
- **THEN** 响应明确返回 `truncated=true` 或 `RESULT_LIMIT_EXCEEDED`，并保留 `queryId` 与已完成阶段，不得静默返回空成功

### Requirement: Execution progress SHALL be observable without inventing backend states

接口 MUST 为测试结果提供可渲染的阶段状态，至少包括参数校验、只读策略校验、获取数据源连接、执行 SQL 和组装 MCP 响应；每个阶段 MUST 有 `PENDING`、`RUNNING`、`SUCCEEDED` 或 `FAILED` 状态及安全的耗时/说明。同步请求在响应返回前可以处于 `RUNNING`，但不得声称尚未发生的阶段已完成。

#### Scenario: Running state is represented consistently

- **WHEN** SQL 正在执行且响应尚未结束
- **THEN** 客户端可根据阶段状态显示“执行中”，已完成阶段保持成功，当前阶段为运行中，后续阶段保持待执行

#### Scenario: Failure identifies the failed stage

- **WHEN** 参数校验、策略校验、连接获取或 SQL 执行任一阶段失败
- **THEN** 响应标识失败阶段、稳定错误码和可恢复提示，后续阶段不得标记为成功

### Requirement: The test console SHALL support high-cardinality parameter contracts

前端 MUST 根据模板返回的参数契约生成可编辑字段，并支持参数搜索、按业务分组折叠、分组完成度、总完成度、必填/类型/说明展示、滚动区域和固定底部操作栏。模板列表和导航 MUST 可收拢为窄栏，收拢后参数区和结果区获得更多可用空间。

#### Scenario: Many parameters remain operable

- **WHEN** 模板包含 18 个或更多参数
- **THEN** 页面仍可通过搜索和分组折叠定位字段，显示 `已填写/总数`，并在未满足必填条件时阻止运行

#### Scenario: Parameter edits are visible before execution

- **WHEN** 管理员修改任一参数值
- **THEN** 完成度、请求 JSON 预览和运行按钮状态同步更新，页面不得只依赖 placeholder 隐式表达字段含义

### Requirement: The console SHALL show request, binding, response, and log views

前端 MUST 提供“请求 JSON”“SQL 绑定”“响应 JSON”“执行日志”四个视图。请求 JSON MUST 展示实际提交的参数值；SQL 绑定 MUST 展示脱敏后的模板/绑定摘要，不得展示可恢复凭证；响应 JSON MUST 展示 Gateway 原始结构化响应；执行日志 MUST 展示阶段和耗时。页面不得将原始结果转换成业务数据表、图表、CSV 或 PNG。

#### Scenario: Raw request and response are copyable

- **WHEN** 测试请求成功
- **THEN** 管理员可以切换并复制请求 JSON 与响应 JSON，内容与服务端响应契约一致

#### Scenario: Sensitive binding details are masked

- **WHEN** 管理员打开 SQL 绑定或执行日志视图
- **THEN** 页面不展示密码、Token、Authorization、密钥材料、完整 JDBC URL 或可恢复的敏感参数值

### Requirement: The management UI SHALL distinguish idle, running, success, and failure states

页面 MUST 为参数未完成、运行中、执行成功、执行失败提供不同的可访问状态、按钮反馈和恢复入口。运行中 MUST 禁用重复提交并允许取消或关闭；失败 MUST 提供稳定错误码、失败阶段和重试/修正参数入口。

#### Scenario: Duplicate execution is prevented

- **WHEN** 测试请求处于运行中
- **THEN** “运行测试”按钮显示加载状态并不可重复提交，页面保留当前参数和阶段进度

#### Scenario: Failed execution can be corrected and retried

- **WHEN** 测试因参数、策略、连接或资源限制失败
- **THEN** 页面保留输入内容，定位失败阶段，管理员修正参数或等待资源后可以重新运行

### Requirement: End-to-end acceptance SHALL prove the real frontend/backend path

变更验收 MUST 同时验证 API 契约、Domain 执行、Infrastructure JDBC 资源释放、Trigger 错误映射和静态前端请求/渲染。仅使用前端 mock 成功状态或仅断言 HTTP 200 不得视为通过。

#### Scenario: Real read-only template test completes end to end

- **WHEN** 使用测试控制库和启用模板从页面提交合法参数
- **THEN** 页面收到真实接口响应，显示执行阶段、queryId、返回行数和原始 JSON，且目标数据库确实执行了参数绑定查询

#### Scenario: End-to-end failure remains safe

- **WHEN** 使用缺失参数、危险 SQL、停用数据源或超时查询进行验收
- **THEN** 页面显示稳定错误和失败阶段，后端不泄露敏感信息，连接/Statement/ResultSet 均被释放
