## Context

现有 MySQL 领域已经具备发布模板解析、数据源解析、参数校验、SQL 安全校验、资源策略合并、JDBC 查询和结构化 `MysqlQueryResult` 能力；`MysqlAdminController#testMysqlTemplate` 目前只查询模板详情，没有调用查询服务。静态管理站点的 SQL 模板工作台也只有一个无参数的测试按钮，无法表达多参数填写、执行阶段或原始 MCP JSON。

本变更跨越 API/Trigger、Case、Domain、Infrastructure 和静态前端，但不需要新的控制库表或 DDL。依赖方向保持 `Trigger/API → Case → Domain ← Infrastructure`；测试执行必须复用正式 MCP Tool 的模板和只读执行链路。

## Goals / Non-Goals

**Goals:**

- 建立可被管理端调用的真实模板测试执行契约，接收参数并返回结构化执行报告。
- 在不改变正式 MCP Tool 返回格式的前提下，增加 queryId、阶段状态、耗时、请求快照和安全的响应包装。
- 为多参数模板提供可搜索、分组折叠、可收拢导航和固定操作区的测试工作区，并严格以 `docs/product/sql-template-test-console-v3.png` 与 `sql-template-test-console-running-v3.png` 为视觉基准。
- 通过单元、集成、真实 MySQL 和前后端联调测试证明参数绑定、资源释放、错误映射和 UI 渲染均走真实链路。
- 将“执行中”定义为真实 HTTP 请求生命周期内的客户端提交状态；后端返回的阶段列表只能反映实际完成/失败的阶段，不伪造异步进度。

**Non-Goals:**

- 不新增或修改 MySQL 控制库表、字段、索引、约束、迁移脚本或异步任务表。
- 不把管理测试改造成新的 Tool 调用协议，不允许前端提交任意 SQL、数据源或执行策略。
- 不提供业务报表、结果表格、图表、CSV/PNG 导出、结果持久化或历史运行列表。
- 不引入 WebSocket/SSE 或后台异步执行；如未来需要长查询实时推送，另立变更。
- 不改变正式 `tools/list`、`tools/call` 的外部 MCP 响应契约。

## Decisions

### 1. 管理测试复用现有模板查询领域服务

`AdminMysqlController` 只负责 DTO 转换和安全错误响应，新增的 MySQL 管理 Case 负责生成 queryId、调用模板查询领域服务并组装测试报告。Case 不直接访问 DAO 或 JDBC；它调用现有 `MysqlTemplateQueryService`，从而保证测试入口和正式 MCP Tool 共用模板状态、固定数据源、参数类型、SQL 安全责任链和资源护栏。

替代方案是 Controller 直接调用 `IMysqlQueryPort`，但这会绕过模板解析、策略合并和领域错误码，导致“测试能过、Tool 不能过”的链路分叉，因此不采用。

### 2. 以显式执行报告承载阶段，而不污染 MCP 查询结果

在 Domain 中增加面向管理测试的执行报告和值对象：

- `MysqlTemplateTestCommand`：模板引用、版本、参数和可选的收紧策略；不含 SQL、JDBC 或密码。
- `MysqlTemplateTestReport`：模板/数据源摘要、queryId、请求安全快照、阶段列表、耗时统计、最终 `MysqlQueryResult` 或稳定错误。
- `MysqlExecutionStage`：固定阶段名、状态、耗时和脱敏说明。

现有 `MysqlQueryResult` 继续作为 MCP 结构化结果，不新增管理字段；正式查询方法保持兼容，管理测试使用带 trace 的领域执行入口，共用同一套校验和端口调用。

阶段分工如下：

| 阶段 | 责任边界 | 失败码示例 |
| --- | --- | --- |
| 参数校验 | Domain 校验模板参数契约和输入类型 | `SQL_PARAMETER_ERROR` / `INVALID_ARGUMENT` |
| 只读策略校验 | Domain 调用现有 SQL Safety Port | `SQL_POLICY_REJECTED` / `SQL_SYNTAX_ERROR` |
| 获取数据源连接 | Infrastructure JDBC 适配器记录连接获取结果 | `DATASOURCE_UNAVAILABLE` |
| 执行 SQL | Infrastructure 绑定 PreparedStatement、执行并释放资源 | `QUERY_TIMEOUT` / `QUERY_EXECUTION_FAILED` |
| 组装 MCP 响应 | Domain/Infrastructure 将列、行、截断信息组装成 `MysqlQueryResult` | `RESULT_LIMIT_EXCEEDED` |

查询端口的 trace 只记录技术阶段，不复制业务判断；技术异常在 Infrastructure 边界转换为 `types.exception` 稳定错误码。阶段说明不得包含完整 SQL、JDBC URL、凭证或原始驱动消息。

### 3. 管理 API 使用显式 JSON 契约

保留现有路径 `POST /admin/test_mysql_template`，但将请求和响应改为真实执行契约。

请求的语义字段：

```json
{
  "id": "900002",
  "version": "1.0.0",
  "parameters": {
    "fromTime": "2026-09-01 00:00:00",
    "toTime": "2026-09-08 00:00:00",
    "channelId": 12
  }
}
```

服务端忽略或拒绝 `sql`、`datasourceRef`、`maxRows`、`timeoutMs` 等不属于测试请求的覆盖字段。成功响应包含 `template`、`datasource`（脱敏）、`queryId`、`requestJson`、`stages`、`metrics`、`responseJson` 和 `result`；失败响应沿用统一 `Response`，只暴露稳定错误码、失败阶段和非敏感修复提示。

`requestJson` 和 `responseJson` 是服务端序列化的安全快照，不能由前端自行拼接作为验收依据。非敏感模板参数可回显实际值；敏感值按统一脱敏策略掩码。`responseJson` 保留 `columns`、`rows`、`rowCount`、`truncated`、`queryId`。

### 4. 同步请求 + 客户端生命周期状态

MVP 保持同步 HTTP 请求，避免引入任务表、轮询或 SSE。前端在请求发送到响应返回期间显示“执行中…”、禁用重复提交、允许取消当前 HTTP 请求；服务器返回的阶段列表只标记真实已完成、失败和未执行阶段。成功态显示全部阶段和原始 JSON，失败态保留参数并显示失败阶段。

如果未来需要跨请求查看长查询进度，应增加独立异步执行契约，不在本变更中把客户端 loading 误称为后端阶段事件。

### 5. 前端采用工作区而非大弹窗

SQL 模板页面进入测试模式后，左侧模板导航收拢为窄栏，右侧测试工作区占据主区域。工作区固定为：

1. 顶部面包屑、模板/数据源/只读执行上下文和关闭入口。
2. 三步轨道：填充参数、执行过程、查看结果。
3. 左侧请求参数面板：搜索、完成度、分组折叠、字段类型/必填/说明、滚动区和固定底部操作栏。
4. 中间可拖拽分隔线，右侧执行过程与原始 JSON 面板。
5. JSON 标签：请求 JSON、SQL 绑定、响应 JSON、执行日志。

参数字段由 `MysqlTemplateDTO.parameters` 动态生成；分组仅为前端展示元数据，不改变服务端参数名。完成度由必填和已填写状态计算，运行前客户端阻止明显缺失，但服务端仍是最终校验者。所有按钮有可见 loading/disabled/focus 状态，错误信息靠近对应字段或失败阶段。

### 6. 前后端联调以服务端响应为单一事实源

前端请求只调用管理 REST，不访问控制库。`mysql-workbench.js` 保留 API 不可用时的安全预览数据，但测试运行按钮不得在预览模式伪造成功；接口不可用时必须显示“无法连接管理 API”。联调验收使用真实应用和测试控制库，断言浏览器请求 payload、HTTP 响应字段、DOM 阶段状态、原始 JSON 内容以及错误码映射。

## Risks / Trade-offs

- **[同步查询耗时较长时浏览器等待] →** 显示真实请求 loading、阶段最终报告和取消入口；不在本变更中引入异步任务，避免持久化和生命周期复杂度扩张。
- **[现有查询端口未暴露连接/执行阶段] →** 只增加内部 trace/阶段回调，不改变 `MysqlQueryResult` 和正式 MCP Tool 契约；基础设施负责技术阶段，Domain 负责业务阶段。
- **[大量参数造成页面滚动和认知负担] →** 使用搜索、分组折叠、完成度、收拢导航、可调分隔线和固定底部 CTA；不通过缩小字体解决密度问题。
- **[请求 JSON 可能回显敏感参数] →** 由服务端统一生成安全快照，按参数契约/敏感名称策略掩码；前端不再自行序列化完整表单作为“原始请求”。
- **[真实 MySQL 环境不稳定] →** Domain/Infrastructure 使用 Mock 和容器测试锁定边界，另用真实只读账号完成至少一条成功、一条参数错误和一条超时/资源限制验收。
- **[旧前端预览数据与新 DTO 字段不一致] →** 增加 normalize 兼容层并保留类型断言；测试 API 可用时必须以真实响应覆盖预览状态。

## Migration Plan

1. 先补 Domain/API/Case/Infrastructure 的失败测试和契约测试，确认现有正式 Tool 调用不回归。
2. 实现管理测试执行链路和错误映射，使用测试控制库验证真实参数绑定与资源释放。
3. 接入 SQL 模板工作区 UI，先支持成功/失败，再接入执行中客户端状态和 JSON 标签。
4. 运行分层测试、浏览器联调、`mvn test`、`git diff --check` 和 `openspec validate --change mysql-template-test-console-execution --strict`。
5. 发布时无需 DDL；若新接口出现问题，可回滚前端入口并恢复旧的模板详情查询，但不得把旧接口伪装为测试成功。

## Open Questions

无。同步请求、无数据库变更、原始 JSON 结构和前端视觉基准均已在本变更中确定。
