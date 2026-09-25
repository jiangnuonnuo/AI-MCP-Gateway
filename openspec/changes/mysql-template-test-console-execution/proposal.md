## Why

当前管理端“测试运行”接口只能覆盖模板预检，无法在同一入口验证已发布 Tool 的真实手动调用，也无法证明 Agent 是否真的通过 MCP 发出了 `tools/call`。现在需要把模板预检、Tool 手动调用和 Agent 调度统一成一个真实测试中心：既能承载大量参数，又能观察执行/Trace，并以真实网关响应而不是前端成功状态作为验收事实。

## What Changes

- 新增 SQL 模板测试执行用例：接收模板标识、版本和参数值，复用已发布模板、固定数据源、参数类型校验、只读 SQL 安全责任链、资源上限和 JDBC 查询端口，返回真实的结构化查询结果。
- 将管理测试接口从“查询模板详情”改为“执行模板测试”，建立稳定的请求/响应 DTO 契约，包含参数回显（按安全规则处理）、请求 JSON、执行阶段、耗时、行数、结果大小、截断状态、响应 JSON 和错误码。
- 增加可观测的执行阶段模型，至少覆盖参数校验、只读策略校验、获取数据源连接、执行 SQL、组装 MCP 响应；同步记录 queryId 和阶段耗时，失败时返回稳定错误码和可恢复提示。
- 将“网关测试”入口升级为统一测试中心，提供“模板预检”“Tool 手动调用”“Agent 调度”三个模式；测试页不提供绑定创建、编辑、删除或启停动作。
- 新增 Tool 手动测试：管理员从当前 Gateway 已存在且启用的 Tool 中选择目标，按实时 Tool schema 填充参数，复用正式 Tool 调用路由，返回真实 MCP 结构化结果、queryId 和阶段报告。
- 新增 Agent 调度测试：管理员只输入自然语言任务和运行约束，已配置 AI 通过 MCP 自主发现/选择 Tool；系统捕获真实 `tools/list`、`tools/call`、Tool 响应、错误和最终回答，不能从最终文本伪造调用记录。
- 将测试工作区扩展为高参数量布局：参数搜索、状态筛选、分组折叠、完成度统计、虚拟滚动、左侧面板收拢和固定操作栏；模板与 Tool 模式复用参数骨架，Agent 模式使用自然语言输入，不出现 Tool 选择器。
- 在测试工作区提供“请求 JSON”“SQL/参数绑定（适用时）”“Tool/Agent Trace”“响应 JSON”“执行日志”视图；原始数据保持 MCP Gateway 结构，不转换为业务表格、图表、CSV 或 PNG。
- 增加 Domain、Infrastructure、Trigger/API、静态前端、真实 MCP SSE/Streamable、已配置 AI 和浏览器联调验收，覆盖真实执行链路、Agent 事件证据、错误边界、敏感信息隔离和视觉回归。

## Capabilities

### New Capabilities

- `mysql-template-test-console`: 统一测试中心能力，覆盖模板预检、Tool 手动调用、Agent 调度、可收拢多参数工作区和原始结果/Trace 查看。

### Modified Capabilities

- `mysql-readonly-template-query`: 管理端测试执行必须调用同一已发布模板查询链路，并返回受资源治理约束的结构化结果。
- `mysql-query-governance`: 模板与手动 Tool 测试必须遵守与正式 MCP Tool 相同的参数绑定、只读策略、资源限制、失败关闭和敏感信息隔离规则；Agent Trace 必须来自真实 MCP 事件并遵守同一脱敏边界。

## Impact

- API/Trigger：`MysqlAdminTestRequestDTO`、Tool 目录/调用 DTO、Agent 调度 DTO、`IAdminMysqlService`、`AdminController`/`AdminMysqlController` 及统一响应错误映射。
- Case/Domain：新增模板、Tool、Agent 测试用例编排，扩展阶段/Trace 结果语义，复用现有模板查询、Tool 绑定、`ToolExecutionRouter`、`MysqlQueryResult` 和 Domain Port。
- Infrastructure：JDBC 查询阶段计时、queryId 关联、MCP 客户端真实事件观察和技术异常转换；不新增表、字段、索引或控制库迁移。
- 前端：`gateway-test` 统一入口、`sql-template.html`/`mysql-workbench.js`、Agent/Tool 测试工作区、API 配置和共享样式；视觉以现有深色测试工作区设计为基准。
- 测试：Domain/Infrastructure/Trigger 单元测试、应用集成测试、真实 MySQL 只读执行、真实 MCP SSE/Streamable、已配置 AI Agent 和静态站点前后端/视觉联调验收。
