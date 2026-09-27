## ADDED Requirements

### Requirement: Dynamic SQL SHALL be governed by the same read-only rule chain as templates

动态 SQL 输入 MUST 进入现有 MySQL SQL 治理责任链。规则选择可以根据模板或动态模式补充模式专属参数规则，但不得通过独立执行器绕过完整 SQL 解析、只读判断和资源规则。

#### Scenario: Dynamic and template policy decisions are equivalent

- **WHEN** 模板 SQL 与动态请求包含语义等价的只读语句、危险语句或无法解析语句
- **THEN** 两种模式返回相同类别的策略允许、策略拒绝或语法错误结果，并在 JDBC 执行前结束拒绝路径

### Requirement: Dynamic SQL SHALL separate SQL text from named parameter values

动态 SQL MUST 将 SQL 文本和命名参数作为两个独立输入，并使用绑定值执行。首版动态协议 MUST 拒绝位置参数、SQL 片段参数、标识符替换和通过字符串拼接形成的执行路径。

#### Scenario: Named parameter is bound safely

- **WHEN** 动态 SQL 使用 `:name` 占位符且 `parameters.name` 类型合法
- **THEN** Gateway 将其转换为 PreparedStatement 参数并执行，不把值拼接回 SQL 文本

#### Scenario: SQL fragment injection is rejected

- **WHEN** 调用方试图通过参数值传入表名、列名、排序片段或完整 SQL 片段
- **THEN** Gateway 将其视为普通绑定值或返回 `SQL_PARAMETER_ERROR`，不得把它当作 SQL 语法执行
