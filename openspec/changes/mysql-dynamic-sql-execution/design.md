## Context

现有运行时将 MySQL Tool 统一解析为 `MYSQL_TEMPLATE`：Session Repository 从 `mcp_protocol_mysql` 读取固定 SQL，`ToolArgumentValidator` 根据模板 mapping 校验参数，`MysqlTemplateExecutor` 构造 `MysqlTemplate` 后交给 `MysqlTemplateQueryService`，最终由 `MysqlJdbcGateway` 绑定并执行。

动态 SQL 不能通过增加一个名为 `sql` 的模板参数实现，因为那会把 SQL 当作值而不是可治理的 SQL 文本，也会被模板参数契约拒绝。动态能力必须在 Tool 执行模式、输入 Schema、SQL 规则责任链和控制面协议记录上显式建模。

## Goals / Non-Goals

**Goals:**

- 以 `TEMPLATE` 和 `DYNAMIC_READONLY` 两种 MySQL 执行模式表达产品规则。
- 让两种模式共享 SQL 解析、只读策略、参数绑定、资源限制、JDBC 执行和结构化结果语义。
- 让外部 MCP Client 通过正式 `tools/list`/`tools/call` 传入动态 SQL并完成真实 MySQL 验收。
- 让规则责任链在模式专属规则和共享规则之间短路，拒绝路径不获取连接。
- 保持 HTTP Tool、模板 Tool、既有 MCP 响应和现有控制面数据兼容。

**Non-Goals:**

- 不在 Gateway 内生成、改写或解释 Text-to-SQL；Agent 只是动态 Tool 的外部调用方。
- 不实现 schema 元数据发现、查询计划、成本估算、审批、SQL 脱敏日志或审计持久化。
- 不支持动态标识符、SQL 片段、列表展开、位置参数、多语句和写事务。
- 不新增独立的动态模板表或第二套 JDBC 执行器。

## Decisions

### 1. 用执行模式选择责任链分支，而不是复制两套服务

新增 `MysqlExecutionMode`，至少包含 `TEMPLATE` 和 `DYNAMIC_READONLY`。Tool 路由先解析模式，再把输入转换为统一的 `MysqlQueryCommand`：

```text
ToolInvocationService
        |
        v
ToolExecutionModeResolver
        |
   +----+----------------+
   |                     |
TEMPLATE          DYNAMIC_READONLY
   |                     |
模板解析/参数契约       SQL/参数请求解析
   +----------+----------+
              v
   共享 MySQL SQL Governance Chain
              |
              v
    统一绑定、资源策略、JDBC Port
              |
              v
       MysqlQueryResult
```

模板分支继续从已发布协议记录解析 SQL 和参数定义；动态分支从 MCP 请求读取 SQL 和命名参数，但两者在进入 JDBC 前必须转换成相同的 SQL、绑定值、数据源引用和有效策略。这样不会因为新增动态模式而复制只读策略、资源限制和结果封装。

候选方案是让动态请求临时构造 `MysqlTemplate`。该方案会伪造模板生命周期字段、绕过模板与动态的模式边界，并使后续授权难以区分，因此不采用。

### 2. 责任链分为模式规则和共享规则

责任链由显式顺序组成，任何规则返回拒绝或解析失败即停止：

```text
请求结构/模式规则
  -> SQL 长度与请求大小
  -> 完整 SQL Parser
  -> 单语句
  -> 只读语句类型
  -> 副作用与危险函数
  -> 动态命名参数/模板参数绑定规则
  -> 数据源与 Tool 资源策略
  -> JDBC 执行
  -> 结构化结果限制
```

模式差异只出现在输入规则：

- 模板模式校验 mapping 中声明的参数、模板占位符和模板状态；
- 动态模式校验顶层只能有 `sql`、`parameters`，参数必须是 JSON 对象，首版只支持命名参数和值类型；
- 共享规则负责完整 SQL 解析、只读判断、单语句、副作用、资源和执行结果。

动态首版拒绝重复命名参数、位置参数、列表展开、表名/列名占位符和任意 SQL 片段参数。数组值不得自动展开为 `IN (...)`，避免把绑定器变成 SQL 生成器。

### 3. 动态 Tool 仍然是 Gateway-scoped 配置

动态 SQL 的数据源和策略不能从请求传入。控制面继续使用 `mcp_gateway_tool` 绑定 Gateway 与 `mcp_protocol_mysql`，在 `mcp_protocol_mysql` 增加执行模式字段：

- `execution_mode=TEMPLATE`：保持现有 `sql_text NOT NULL` 和 mapping 语义；
- `execution_mode=DYNAMIC_READONLY`：SQL 正文由调用方传入，协议记录只保存固定数据源、资源策略和状态，`sql_text` 允许为空；
- 旧记录回填为 `TEMPLATE`；未知模式拒绝运行。

不新增动态协议表，不复用 `tool_type=function/resource` 表达执行模式，也不把动态 Tool 变成全局特殊工具。该字段和 `sql_text` 可空性属于控制库结构变更，实施前必须完成 ER/字段兼容/迁移确认；确认前不得编写 DDL、DAO 或依赖该字段的生产代码。

### 4. 动态 Tool Schema 由模式直接生成

模板 Tool 继续从 request mappings 生成固定参数 Schema。动态 Tool 不使用模板 mapping 作为参数白名单，而由模式生成：

```json
{
  "type": "object",
  "properties": {
    "sql": {"type": "string"},
    "parameters": {"type": "object", "additionalProperties": true}
  },
  "required": ["sql", "parameters"],
  "additionalProperties": false
}
```

Schema 只描述输入形状；数据源、策略、凭证和服务端资源上限永远不进入 MCP discovery 或调用参数。

### 5. 复用现有 JDBC 适配器和结果模型

Infrastructure 只扩展 SQL 文本与命名参数绑定能力，使模板和动态请求都生成 `BoundSql` 后进入现有 `MysqlJdbcGateway`。不新增第二个连接池、第二个结果协议或绕过 `IMysqlQueryPort` 的执行器。

动态查询错误继续映射为既有稳定错误码，包括 `INVALID_ARGUMENT`、`SQL_PARAMETER_ERROR`、`SQL_SYNTAX_ERROR`、`SQL_POLICY_REJECTED`、`DATASOURCE_UNAVAILABLE`、`QUERY_TIMEOUT`、`RESOURCE_LIMIT_EXCEEDED` 和 `RESULT_LIMIT_EXCEEDED`。

### 6. 本轮验收只证明执行闭环

真实验收必须新建一个专用 Gateway，不复用模板验收 Gateway，也不通过内存配置或前端 Mock 代替控制面链路。该 Gateway 必须完成以下真实绑定：

```text
新 Gateway
  -> 新建/启用 Gateway Tool
  -> 绑定 DYNAMIC_READONLY MySQL 协议
  -> 绑定真实只读数据源
  -> tools/list
  -> tools/call
  -> 真实 MySQL 结果
```

验收先通过该 Gateway 的 SSE 和 Streamable MCP 端点调用 `tools/list`，确认仅返回当前 Gateway 已启用的动态 Tool 及 `sql`/`parameters` Schema；再调用同一个 Tool 执行成功 SQL 和拒绝 SQL，检查真实数据库结果、稳定错误码、无连接拒绝和资源释放。必须核对 Gateway、Tool、协议、数据源四者的持久化绑定与运行时解析一致。

保留现有 `queryId`、执行阶段和 MCP 响应字段用于关联，但不增加日志格式、日志级别、审计存储或查询接口。

## Risks / Trade-offs

- **控制库字段变更影响旧记录** → 旧行回填 `TEMPLATE`，未知/空模式失败关闭；先完成字段矩阵、迁移脚本和回滚方案再实现代码。
- **动态请求缺少模板类型契约** → 首版限制 JSON 标量值和命名参数，JDBC 绑定失败返回稳定参数错误；复杂类型另立变更。
- **SQL Parser 对 MySQL 方言覆盖不足** → 解析失败关闭，真实验收只纳入已验证的 SELECT/CTE/JOIN/聚合语法。
- **动态能力扩大数据访问面** → 仍要求显式 Tool、固定数据源、只读账号和服务端资源策略；未启用动态模式不进入解析器。
- **Agent 生成错误 SQL** → Gateway 返回稳定拒绝结果，Agent 自行决定是否重新生成；Gateway 不自动修复或重写 SQL。

## Migration Plan

1. 先确认 `mcp_protocol_mysql.execution_mode`、`sql_text` 可空性、默认值、检查约束、索引和回滚方案，并更新 ER 与字段矩阵。
2. 回填现有 MySQL 协议为 `TEMPLATE`，验证既有模板 Tool、HTTP Tool 和 `tools/list`/`tools/call` 回归。
3. 实现共享查询命令、模式分支责任链、动态 Schema、动态执行器和绑定器扩展。
4. 新建专用 Gateway，创建并启用动态 Tool，绑定专用动态协议记录和固定只读数据源，执行真实 MCP SSE/Streamable 验收。
5. 若回滚，只关闭动态模式并恢复旧模板路由；不删除现有模板、Tool 绑定或数据源数据。

## Open Questions

无。动态 SQL 的调用来源、Gateway 职责、模式区分、责任链边界、控制面归属和本轮验收范围已经确定；日志和审计增强属于后续变更。
