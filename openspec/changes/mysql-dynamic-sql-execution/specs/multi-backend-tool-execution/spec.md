## ADDED Requirements

### Requirement: Tool routing SHALL distinguish dynamic MySQL execution from template execution

Gateway MUST 将动态只读 MySQL Tool 解析为独立执行模式，并根据该模式选择动态执行器；不得把外部 SQL 伪装成模板 SQL，也不得把动态 MySQL 配置路由到 HTTP 执行器。

#### Scenario: Dynamic MySQL Tool uses the dynamic executor

- **WHEN** 已启用 Tool 的执行模式为动态只读 MySQL
- **THEN** Gateway 读取调用参数中的 SQL 和命名参数，调用 MySQL 动态执行路径并通过 JDBC 执行

#### Scenario: Template routing remains unchanged

- **WHEN** 已启用 Tool 的执行模式为 MySQL 模板
- **THEN** Gateway 读取服务端模板正文和模板参数契约，继续调用模板执行路径

### Requirement: Dynamic Tool requests SHALL be validated before backend access

动态 Tool 请求 MUST 在获取 MySQL 连接前校验顶层字段、SQL 字符串、命名参数对象、Tool 状态、调用权限和请求大小。动态请求不得被通用模板参数校验器误判为模板参数调用。

#### Scenario: Dynamic request has the required shape

- **WHEN** 请求包含 `sql` 字符串和 `parameters` 对象且没有客户端后端覆盖字段
- **THEN** Gateway 进入 SQL 治理责任链；在治理通过前不获取连接

#### Scenario: Dynamic request has an invalid shape

- **WHEN** 请求缺少 SQL、参数不是对象、包含不允许的 SQL 覆盖字段或 Tool 已停用
- **THEN** Gateway 返回稳定的 `INVALID_ARGUMENT`、`ACCESS_DENIED` 或 `TOOL_DISABLED`，不执行后端操作
