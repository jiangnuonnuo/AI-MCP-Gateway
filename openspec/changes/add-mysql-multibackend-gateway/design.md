## Context

现有 MCP 调用路径由 `ToolsCallHandler` 直接读取 Tool 协议配置，并通过 `ISessionPort.toolCall(HTTPConfig, ...)` 调用 HTTP。`McpToolProtocolConfigVO`、`SessionRepository` 和 `SessionPort` 都假定协议是 HTTP；调用结果主要按文本返回，参数处理还存在只取顶层第一个参数的问题。

验收环境已经确定为本机 MySQL `127.0.0.1:3306` 的 `data_warehouse` 数据库。当前 Gateway 自身仍需要访问配置库（现有环境通常为 `ai_mcp_gateway_v2`），因此目标数仓和 Gateway 配置库必须建模为两个逻辑数据源，即使它们位于同一个 MySQL 实例。目标库凭证不能写入仓库；用户名和密码由运行时环境注入，且本计划不记录密码。

项目采用 `Trigger -> API -> Case -> Domain <- Infrastructure` 的 DDD/六边形结构。Gateway 自身的 MySQL 配置库继续由现有 MyBatis Repository 访问；本变更新增的是连接业务数仓的目标 MySQL 数据源。目标 MySQL 是外部系统，不能让 Domain 依赖 JDBC、MyBatis 或连接池实现。

## Goals / Non-Goals

**Goals:**

- 以最小纵向切片验证 `tools/list -> tools/call -> MySQL/JDBC -> MCP structured result` 的真实链路。
- 同时保留现有 HTTP Tool 执行能力，通过统一执行器路由支持多后端。
- MVP 仅发布 MySQL 只读查询模板 Tool，支持参数绑定、JOIN/聚合查询、结果限制和 SQL 安全拒绝。
- 通过可替换的配置 Registry 和执行 Port，为后续管理配置持久化、动态 SQL 和其他后端预留扩展点。
- 让 SQL 安全和数据库权限形成双重防护，并使拒绝、超时和执行结果可测试、可审计。

**Non-Goals:**

- 本变更不提供 INSERT、UPDATE、DELETE、DDL、事务或任何写操作。
- 本变更不实现数据字典、表结构搜索、指标语义和业务 SQL 自动生成。
- 本变更不实现动态 SQL Tool、异步大查询、游标分页、结果文件导出和字段级脱敏；这些作为后续变更。
- 本变更不在未确认数据结构前修改 Gateway 配置库 DDL、DAO、PO 或 Mapper。

## Decisions

### 1. 使用多后端执行器策略路由

在 Domain 定义后端无关的 Tool 执行上下文、结果和执行端口；使用策略注册表按 `backendType + executionMode` 选择执行器。

```text
ToolCallCase
    -> ToolExecutorRouter
       -> HttpToolExecutor
       -> MysqlTemplateExecutor
```

新增执行器不修改 MCP Handler 的主流程。HTTP 和 MySQL 执行器分别负责各自的技术访问，统一返回领域结果和稳定错误。

替代方案：在 `ToolsCallHandler` 或 `SessionPort` 中增加 `if (http/mysql)` 分支。该方案会把协议、会话和数据库技术细节混在一起，后续增加 Dubbo、消息队列或动态 SQL 时会持续膨胀，因此不采用。

### 2. 将 Tool 执行从 SessionPort 中拆出

现有 `ISessionPort` 同时承载 Redis 会话同步和 HTTP Tool 调用。MVP 将新增独立的 Tool 执行 Port，由 `ToolsCallHandler` 所属用例调用；`ISessionPort` 保留会话同步职责。这样 MySQL/JDBC 不会被错误地建模为 Session 能力。

Domain 只依赖类似以下抽象：

```text
IToolExecutionPort.execute(ToolExecutionContext)
IMysqlQueryPort.execute(MysqlQueryCommand)
ISqlSafetyPort.validate(SqlStatement)
```

Infrastructure 的 `adapter/port` 实现这些接口，调用 `gateway/` 下的 HTTP 客户端或 JDBC 客户端；Gateway 控制库的 Tool、数据源和模板元数据仍由 `adapter/repository` 调用 `dao/` 访问。

### 3. MVP 先使用本机真实数仓和测试 Registry 打通链路，再持久化管理配置

当前表结构只描述 HTTP 协议，且新增目标数据源、模板和策略涉及兼容性与密钥存储。为先验证执行链路，MVP 的第一个纵向切片使用测试 profile 下的受控数据源和模板 Registry，实际连接本机 `127.0.0.1:3306/data_warehouse`；不立即修改 Gateway 配置库 DDL，也不向目标数仓写入测试数据。

首次验收先执行 `SELECT 1` 和只读元数据查询，确认当前运行账号可以连接目标库并读取实际表、列信息。模板不得假设存在订单表、门店表或其他业务表；只有在只读探查确认表和字段后，才选择对应的 JOIN/聚合查询作为验收模板。若目标库没有适合的关联数据，使用独立测试实例作为补充，不在 `data_warehouse` 中创建 fixture。

链路验证成功后，再单独确认数据源、模板、策略和 Tool 绑定的表结构、迁移和管理 API。持久化方案不得反向改变已经验证的执行契约。

替代方案：第一步直接设计完整配置表和管理页面。该方案把数据库迁移风险与 JDBC 执行风险绑定，无法快速判断核心链路是否可行，因此不采用。

### 4. MySQL 采用只读模板执行器

MVP 的 MySQL Tool 使用固定数据源和已发布模板：

```text
Tool -> datasourceRef + templateRef + inputSchema + policyRef
```

执行器使用 PreparedStatement 绑定参数，设置只读连接和查询超时，执行结果转换为 `columns/rows/rowCount/truncated/queryId`。

模板本身允许 JOIN、聚合、子查询和合法 CTE SELECT；Gateway 不解释业务指标，也不维护数据字典。

### 5. SQL 安全采用解析器适配器加责任链

Domain 表达只读策略和拒绝结果，Infrastructure 提供支持 MySQL 方言的 SQL Parser 适配器。安全检查按显式顺序执行：

```text
Parse -> SingleStatement -> StatementType -> SideEffect/Lock
      -> Parameter -> ResourcePolicy
```

无法解析、无法判断或不支持的语句一律失败关闭。禁止仅使用字符串前缀或正则表达式判断安全性。数据库目标账号同时只授予查询权限，形成第二道防线。

SQL Parser 依赖在实现前通过 MySQL 语法矩阵测试确定；依赖选择不改变对外只读契约。

### 6. 统一结构化结果和错误模型

MCP Tool 执行结果使用领域 `ToolExecutionResult`，成功时包含结构化数据和可关联的查询标识；失败时使用稳定的错误码。`ToolsCallHandler` 不再把所有结果强制转成文本，也不再以字符串形式返回 `isError`。

HTTP 结果也经过同一结果封装，但保留现有 Tool 的兼容表示，避免 MySQL 扩展破坏 HTTP 客户端。

### 7. 采用测试优先的纵向切片

每一个实现阶段先写失败测试，再完成最小实现。首个验收测试使用独立的 MySQL 测试实例和隔离数据，验证真实 JDBC 查询，而不是只用 Mock JDBC。安全拒绝测试覆盖写操作、多语句、锁定和解析失败；HTTP 回归测试保证现有路径不变。

## Risks / Trade-offs

- [目标 MySQL 凭证泄露] -> 仅保存密钥引用；日志、错误和审计禁止输出密码、Token、Authorization 和完整敏感结果。
- [SQL Parser 对 MySQL 方言支持不足] -> 先建立语法矩阵；解析失败拒绝执行；在未验证前不开放动态 SQL。
- [只读账号配置错误导致写入风险] -> SQL AST 拒绝作为第一道防线，数据库账号权限作为第二道防线，增加启动/测试权限检查。
- [查询耗尽连接池] -> 每个数据源使用独立连接池，限制连接、并发、超时和结果大小，所有异常路径归还资源。
- [MCP Handler 被后端细节污染] -> 通过执行器策略和独立 Tool Execution Port 路由，Handler 只处理 MCP 请求和用例调用。
- [配置库 DDL 过早固化] -> MVP 先使用配置 Registry/测试替身，表结构确认后再单独提交迁移变更。
- [大结果集阻塞同步 MCP 调用] -> MVP 限制结果规模并明确超限行为；异步查询、游标和导出不在本变更内。
- [动态 SQL 绕过模板治理] -> MVP 不发布动态 SQL Tool，并在权限层保留明确的执行模式开关。

## Migration Plan

1. 增加执行器抽象和 HTTP 兼容适配，先运行现有 HTTP Tool 回归测试。
2. 增加测试 profile 的目标 MySQL 数据源和固定只读模板 Registry，连接本机 `127.0.0.1:3306/data_warehouse`；使用运行时凭证注入，不修改默认 Gateway 配置库连接。
3. 先执行 `SELECT 1` 和只读元数据检查，再依据实际表结构注册 MySQL Tool 的模板；验证结构化结果、超时、资源释放和错误码。
4. 增加 SQL 安全责任链及拒绝测试，确认所有写入和副作用语句在 JDBC 执行前被拦截。
5. 评审测试链路和配置模型后，再设计并实施数据源、模板、策略和 Tool 绑定的持久化结构及管理 API。

回滚策略：MySQL Tool 默认不发布；关闭 MySQL 执行器或删除测试 profile 配置即可停止新能力。HTTP Tool 使用旧执行路径或兼容适配器继续运行。验收期间不修改 `data_warehouse` 对象和数据，因此无需对目标库执行回滚。任何配置库迁移必须可独立回滚，不与执行器代码强绑定。

## Open Questions

- 无会改变本次 MVP 规格或执行链路的未决问题。SQL Parser 的具体依赖可在实现任务的技术验证中确定，只要满足 MySQL 方言解析和失败关闭要求。
