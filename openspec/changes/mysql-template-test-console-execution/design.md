## Context

现有变更已经落地 SQL 模板测试工作区和 MySQL 只读查询报告：模板解析、参数校验、只读策略、数据源解析、资源护栏、JDBC 查询和结构化 `MysqlQueryResult` 均已有正式链路。当前缺口是测试入口仍以模板为中心，无法在同一工作区执行已发布 Tool，也无法证明 Agent 是否通过 MCP 真正发起了 `tools/call`。

当前正式 Tool 链路由 `ToolsCallHandler` 接收 JSON-RPC，解析当前 Gateway/Tool 绑定后交给 `ToolExecutionRouter` 和具体执行器；`AdminLLMService` 使用已配置的 `ChatModel` 连接 Gateway MCP endpoint，但现在只返回最终文本。静态站点已有 `gateway-test` 导航和 SQL 模板工作区，需要在其上统一入口和结果模型。

系统继续遵循 `Trigger/API → Case → Domain ← Infrastructure`。AI、Gateway、Tool、数据源、鉴权和测试数据视为当前环境已填充的前置条件，不新增控制库表或把测试页面变成绑定管理页面。

## Goals / Non-Goals

**Goals:**

- 在现有未归档变更中统一模板预检、Tool 手动调用和 Agent 调度三种测试模式。
- 手动 Tool 测试与正式 MCP `tools/call` 共享相同的绑定、参数、只读、资源和结果语义。
- Agent 测试捕获 MCP 传输层真实事件，能证明 0..N 次 Tool 调用、响应、错误和最终回答的因果顺序。
- 保持多参数工作区的搜索、分组折叠、完成度、左侧收拢、固定操作栏和原始 JSON 结果体验。
- 以真实已配置 AI、真实 MCP SSE/Streamable、真实只读数据源和浏览器联调作为验收事实。

**Non-Goals:**

- 不新增 AI 提供商、模型编排、对话历史、异步任务队列或测试历史持久化。
- 不修改控制库 DDL，不创建/修改/删除/启停 Tool 或绑定，不复制配置数据。
- 不允许前端提交任意 SQL、数据源、行数、超时或策略覆盖，不转换结果为业务表格、图表、CSV/PNG。
- 不通过解析最终回答、普通日志或客户端 loading 伪造 Tool 调用和后端阶段；异步长任务另立变更。

## Decisions

### 1. 统一入口和模式状态

保留侧边栏 `gateway-test` 位置，将页面语义升级为“测试中心”。顶栏固定显示 Gateway 上下文、模式标签和状态标识：

1. `模板预检`：复用本变更已经完成的 SQL 模板测试契约；
2. `Tool 手动调用`：从当前 Gateway 已存在且启用的 Tool 目录选择目标并填写参数；
3. `Agent 调度`：只填写自然语言任务和运行约束，不出现 Tool 选择器。

模式切换只改变测试请求状态，不产生配置写操作。三个模式共享执行阶段/Trace 区、原始 JSON 标签和底部执行栏；模式特有的参数输入由模板/Tool schema 或 Agent 任务输入驱动。

### 2. Tool 手动调用复用正式领域语义

新增管理测试 Case 不直接调用 DAO、JDBC 或模板查询实现，而是调用可被 MCP Handler 与管理 Case 共享的 Domain Tool 调用入口。领域流程保持：

```text
当前 Gateway/Tool 目录与绑定解析
        ↓
访问策略 + Tool 参数契约校验
        ↓
服务端解析协议/模板/数据源配置
        ↓
ToolExecutionContext
        ↓
ToolExecutorRouter / 既有执行器
        ↓
脱敏 ToolExecutionResult + 阶段报告
```

`ToolsCallHandler` 继续负责 MCP JSON-RPC 适配；管理端只新增测试报告包装，不复制执行器。两条入口必须对同一 Tool/参数得到相同的 `columns`、`rows`、`rowCount`、`truncated`、错误和资源释放语义。

建议管理端契约如下，实际前缀遵循现有 Controller 命名规范：

- `GET /admin/test-center/{gatewayId}/tools`：返回启用 Tool 和脱敏输入 schema；
- `POST /admin/test-center/tool-call`：接收 Gateway、Tool 名称、参数对象并执行一次测试；
- `POST /admin/test_mysql_template`：继续作为模板预检兼容入口，由统一页面模板模式调用。

Tool 报告包含 `testId`、Gateway/Tool 摘要、`queryId`（若有）、脱敏 request JSON、阶段、耗时、原始 MCP response 和稳定错误码。请求 DTO 明确拒绝或忽略 SQL、数据源、行数、超时和策略覆盖字段。

### 3. 模板预检保持单一事实源

模板模式复用既有 `MysqlTemplateQueryService` 和报告契约，不重新实现 SQL 解析、参数绑定、只读策略或 JDBC 生命周期。模板预检与同一模板 Tool 手动测试的真实验收使用同一组参数，对比结构化结果、截断语义、错误码和资源释放；管理端 JSON 只由服务端生成，不能由前端表单自行拼接为“原始响应”。

### 4. Agent Trace 在 MCP 客户端边界捕获

当前 `AdminLLMService` 的 `ChatModel.call` 只保留最终字符串。增加请求作用域的 Domain trace 端口，由 Infrastructure 在 MCP Client/transport 边界接收真实事件，不解析最终回答补齐事件：

```text
AGENT_STARTED
  → MCP_SESSION_OPENED
  → TOOLS_LIST_REQUEST / TOOLS_LIST_RESPONSE
  → TOOL_CALL_REQUEST
  → TOOL_CALL_RESPONSE 或 TOOL_CALL_ERROR
  → MODEL_MESSAGE / AGENT_FINISHED
```

事件统一携带 `agentTestId`、requestId、Tool 名称、脱敏参数、下游 queryId（若产生）、状态、时间戳、耗时和安全摘要。支持零次、单次、多次和并行 Tool 调用，使用关联字段表达并行关系。观察端故障不能改变正式 Tool 结果；必需事件无法从传输层核验时报告标记 `UNOBSERVED`，真实验收不得通过。

建议 Agent 管理契约为 `POST /admin/test-center/agent-run`，输入 Gateway、自然语言任务、MCP 类型、鉴权引用和超时，不包含 `toolName`；响应包含 `agentTestId`、状态、事件数组、最终回答、脱敏 request/response JSON 和稳定错误。

### 5. 分层职责和模型边界

- **API/Trigger**：定义 Tool 目录、手动调用、Agent 调度 DTO，负责 HTTP 校验、统一 Response 和非敏感错误映射。
- **Case**：编排测试用例、生成 testId/agentTestId、组合报告和请求作用域生命周期，不访问 DAO/JDBC。
- **Domain**：定义 Tool 测试命令、阶段/Trace 事件、报告值对象和共享 Tool 执行入口，保持业务校验与状态规则。
- **Infrastructure**：实现 Tool 目录/绑定 Repository、既有执行器、JDBC 阶段计时和 MCP Client 事件观察，负责外部技术异常转换与资源释放。
- **前端**：渲染模式、参数/任务输入、状态机、Trace 和原始 JSON，不访问控制库，不生成伪成功。

跨层模型只传稳定业务字段；JDBC、HTTP 客户端、MCP SDK、环境变量和凭证不穿透 Domain。

### 6. 多参数测试工作区

统一页面采用深色产品图基准和“左请求、右执行/结果”布局：

- 顶栏：返回、Gateway/模式标题、三个模式标签、运行状态和测试标识；
- 左栏：搜索、全部/未填写/错误筛选、分组折叠、分组/总完成度、字段类型/必填/说明；支持收拢成窄 rail；
- 右主区：请求摘要、执行阶段或 Agent Trace 时间线、JSON 标签；
- 底栏：固定重置、取消、执行 CTA，运行中禁用重复提交；
- 大列表：窗口化渲染或等价虚拟滚动，搜索/折叠/收拢不重置字段值。

模板与 Tool 模式使用 schema 驱动字段，Agent 模式使用自然语言任务和运行约束。标签包括请求 JSON、适用 SQL/参数绑定、Tool/Agent Trace、响应 JSON、执行日志。结果不显示业务表格或导出按钮；错误卡片、右上角无关搜索和默认绿色边框等不属于测试语义的旧组件应移除或修正。

### 7. 安全快照与生命周期

统一脱敏器处理密码、Token、Authorization、模型密钥、JDBC URL 和敏感参数；日志仅记录关联标识和非敏感摘要。Infrastructure 在 MCP/JDBC/HTTP 边界转换稳定错误码并释放资源。测试报告只在请求生命周期内存储，超时/取消停止后续调用并释放 MCP 客户端、Statement、ResultSet 和 Connection。

### 8. 真实验收矩阵

使用当前已填充环境，不使用 stub 成功响应：

| 层级 | 验收事实 |
| --- | --- |
| Domain/Case | 合法/缺失/多余/类型错误参数、停用 Tool、策略拒绝、资源/超时错误有稳定错误码和正确后续阶段 |
| Tool 手动 | 同一 Tool/参数分别经管理端和正式 MCP `tools/call`，结构化结果和错误治理一致 |
| MCP 传输 | SSE 与 Streamable HTTP 各执行至少一次，真实 `tools/list`/`tools/call` 与报告事件逐项对应 |
| Agent | 已配置 AI 自动选择 Tool，报告包含实际请求、参数、响应、最终回答；零调用任务单独验收 |
| 前端 | 三模式、36+ 参数搜索/折叠/收拢、running/success/failure、原始 JSON、无错误卡片/伪成功 |
| 回归 | 模板预检、Tool 调用、既有 MCP `tools/list`/`tools/call` 和 MySQL 资源释放测试均通过 |

验收证据保存脱敏 request payload、响应 JSON、事件序列、网络抓包摘要、queryId/agentTestId 和浏览器截图。

## Risks / Trade-offs

- **MCP 客户端未暴露全部事件回调** → 在传输/Handler 边界增加最小观察适配器；不可观测事件标记 `UNOBSERVED`，禁止用最终文本代替。
- **Agent 或查询同步等待时间较长** → 首期保留同步 HTTP，显示真实 pending 和取消；不引入任务表和伪进度。
- **大量参数影响首屏性能** → 使用搜索、分组和窗口化渲染，服务端继续作为完整校验事实源。
- **手动 Tool 与外部 MCP 语义漂移** → 共享 Domain Tool 调用入口，并对同一 Tool/参数执行双路径真实对比。
- **Trace 泄露敏感上下文** → 所有事件在报告边界前脱敏，日志不保存原始凭证和敏感值。
- **旧 UI 残留错误卡片/样式** → 以 DOM 契约和截图回归作为发布门槛，删除旧组件而不是仅用 CSS 隐藏。

## Migration Plan

1. 保留已完成的模板预检实现和任务证据，补充 Tool/Agent 契约失败测试及安全快照测试。
2. 抽取共享 Domain Tool 调用入口，接入手动 Tool Case 和既有 MCP Handler 回归测试。
3. 在已配置 AI 的 MCP 客户端边界接入 Trace observer，完成零/单/多 Tool、失败、超时和取消测试。
4. 将 `gateway-test` 页面迁移为统一测试中心，接入 Tool schema、Agent 输入、高参数量布局和原始 JSON/Trace。
5. 用真实 MySQL、SSE、Streamable HTTP、已配置 AI 和浏览器执行全量验收，保存脱敏证据。
6. 回滚只恢复旧测试入口和管理路由，不回滚 Tool 绑定或数据库数据；不需要 DDL 回滚。

## Open Questions

无。模板预检、Tool 手动调用、Agent 自动调度的边界、真实事件证据、统一入口和多参数布局均已确定；endpoint 前缀和前端组件名称可按现有代码规范实现，不改变外部行为。
