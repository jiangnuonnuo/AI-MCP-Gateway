## 1. 契约与失败测试

- [x] 1.1 扩展 `MysqlAdminTestRequestDTO` 和新增测试响应 DTO，定义模板标识、版本、参数对象、queryId、请求安全快照、阶段、指标和原始结果字段；用 Trigger 契约测试锁定 JSON 字段名、必填规则和不接受 SQL/数据源覆盖字段的行为。
- [x] 1.2 为 `MysqlTemplateTestReport`、`MysqlExecutionStage` 和阶段状态值对象编写 Domain 失败测试，覆盖固定五阶段、阶段顺序、成功/运行中/失败状态及耗时不可为负。
- [x] 1.3 为 `MysqlTemplateQueryService` 的测试执行入口编写失败测试，覆盖缺失参数、未声明参数、类型不匹配、停用模板、停用数据源、策略拒绝和 queryId 关联；验证失败时不会调用查询端口。
- [x] 1.4 为请求/响应安全快照编写失败测试，验证密码、Token、Authorization、JDBC URL、密钥材料和敏感参数不会进入响应、日志或阶段说明。

## 2. Domain 与 Case 执行链路

- [x] 2.1 实现带执行报告的模板查询流程，复用现有模板解析、数据源解析、参数校验、SQL Safety Port、策略合并和 `MysqlQueryCommand`；验证正式 `execute` 方法和 MCP Tool 行为保持兼容。
- [x] 2.2 在 Domain 侧记录参数校验和只读策略校验阶段，在失败路径停止后续阶段并返回稳定错误码；运行 `MysqlTemplateQueryServiceTest` 及新增领域测试确认阶段状态。
- [x] 2.3 新增 MySQL 管理测试 Case，生成 queryId、调用 Domain 查询服务、组装管理测试报告和安全 JSON；验证 Case 不直接依赖 DAO、JDBC 或 Infrastructure 实现。
- [ ] 2.4 为 Case 增加成功、参数错误、模板不可用、数据源不可用、策略拒绝、结果截断和查询超时测试；每个测试断言失败阶段、错误码和后续阶段未被错误标记为成功。

## 3. Infrastructure 阶段与资源治理

- [x] 3.1 扩展现有 MySQL 查询 Port 的 trace 能力，在 `adapter/port` 内记录获取连接、执行 SQL、结果组装阶段，不新增绕过 Port 的 DAO/Service 包；用 Mock JDBC 测试验证阶段顺序和技术异常转换。
- [ ] 3.2 验证 PreparedStatement 参数绑定、最大行数/列数/字节数、超时、取消和 ResultSet/Statement/Connection 释放；运行现有 `MysqlQueryResultTest`、`MysqlReadonlyPrivilegeIntegrationTest` 与新增生命周期测试。
- [ ] 3.3 将 JDBC、认证、超时、资源限制异常转换为稳定 `types.exception` 错误码并脱敏日志；增加日志断言确保不出现密码、完整 JDBC URL、驱动堆栈或完整敏感参数。
- [ ] 3.4 使用测试 MySQL/容器或现有真实只读数据源执行至少一条成功查询、一条参数绑定查询、一条只读策略拒绝和一条超时/结果限制场景；保存 queryId、阶段耗时和资源释放验收证据。

## 4. Trigger/API 对接

- [x] 4.1 修改 `IAdminMysqlService` 与 `AdminMysqlController#testMysqlTemplate`，把旧的“返回模板详情”行为替换为真实测试 Case 调用；Controller 只做 DTO 转换、统一 Response 封装和安全错误映射。
- [x] 4.2 为管理测试接口增加请求校验和兼容错误映射，验证 HTTP 成功响应包含报告而非模板详情，HTTP 失败响应包含稳定错误码、失败阶段和可读修复提示。
- [x] 4.3 扩展 `MysqlAdminControllerContractTest`、`AdminControllerTest` 或对应 Trigger 测试，覆盖合法多参数 JSON、空参数、未知字段、错误码、敏感字段隔离和 queryId 返回。
- [x] 4.4 用 Mock Case 验证 Controller 不会调用 DAO/Repository、不执行第二条查询、不接受前端传入的 SQL/数据源/策略覆盖，并执行 `mvn -pl ai-mcp-gateway-app -am test` 的接口测试子集。

## 5. 前端测试工作区

- [x] 5.1 按 `docs/product/sql-template-test-console-v3.png` 和 `docs/product/sql-template-test-console-running-v3.png` 重构 `sql-template.html`：实现收拢模板导航、三步轨道、参数面板、执行面板、可调分隔线、JSON 标签和固定底部操作栏；用静态截图检查布局层级和视觉基准一致。
- [x] 5.2 在 `mysql-workbench.js` 中根据模板参数契约动态生成字段，支持类型、必填、说明、参数搜索、分组折叠、分组完成度、总完成度和大量参数滚动；用浏览器脚本验证 18+ 参数仍可搜索、折叠、填写和定位。
- [x] 5.3 实现参数校验和客户端状态机：未完成阻止提交，HTTP pending 显示“执行中”并禁用重复提交，成功展示全部阶段和响应 JSON，失败保留参数并显示失败阶段；用 DOM 断言覆盖 idle/running/success/failure 四态。
- [x] 5.4 实现请求 JSON、SQL 绑定、响应 JSON、执行日志四个视图，所有 JSON 由服务端报告渲染；验证页面不显示业务表格、图表、CSV/PNG 导出，也不在接口不可用时伪造成功。
- [x] 5.5 增加可访问性和交互细节：可见 label、键盘可达折叠/搜索/复制/关闭、按钮 loading/disabled/focus 状态、错误靠近字段或阶段；用浏览器键盘操作和无障碍检查确认无 placeholder-only 字段。

## 6. 前后端联调与真实验收

- [x] 6.1 使用真实应用和测试控制库，从静态站点提交合法多参数模板测试，抓取网络请求并断言 payload、响应 DTO、queryId、阶段顺序、指标和原始 JSON 与后端报告一致。
- [ ] 6.2 联调缺失/多余/类型错误参数、停用模板、停用数据源、危险 SQL、超时和结果限制场景；验证页面显示稳定错误码和恢复入口，后端不获取连接或不泄露敏感信息。
- [x] 6.3 验证正式 MCP `tools/call` 与管理端测试使用相同模板、数据源、参数绑定和只读治理规则；对同一模板输入断言结构化 `columns`/`rows` 语义一致。
- [x] 6.4 进行视觉回归：在桌面宽屏、窄屏和参数 18+ 场景截图，与两张产品基准图逐项检查导航收拢、分组折叠、执行阶段、JSON 阅读区和 CTA 位置；修复布局溢出后再继续。
- [x] 6.5 执行完整验收命令 `mvn test`、`git diff --check`、`openspec validate --change mysql-template-test-console-execution --strict`，并记录真实 MySQL/浏览器联调证据；只有所有分层测试和端到端测试通过才标记变更完成。

## 7. 统一测试中心与 Tool 手动调用

- [x] 7.1 固化统一测试中心、Tool 目录/调用和 Agent 调度的请求/响应 DTO、模式状态、阶段、Trace 事件及 testId/queryId/agentTestId 关联字段；验证 API 契约测试锁定字段名、必填规则和 Agent 请求不包含 `toolName`。
- [x] 7.2 抽取可被管理 Case 与 MCP `ToolsCallHandler` 共同使用的 Domain Tool 调用入口，复用当前 Gateway/Tool 绑定、访问策略、`ToolExecutionContext`、`ToolExecutorRouter` 和执行器；验证两种入口对同一 Tool/参数返回等价结果。
- [x] 7.3 新增当前 Gateway 可测试 Tool 目录接口和 schema 驱动的手动调用 Case；验证启用/停用/解绑/无权限场景、未知 Tool、缺失/多余/类型错误参数，以及不接受 SQL、数据源、行数、超时和策略覆盖字段。
- [x] 7.4 为 Tool 手动测试补齐真实报告：testId、queryId、脱敏 request JSON、阶段、耗时、结果 JSON、稳定错误码和资源释放信息；验证参数/策略/数据源/资源/超时失败时后续阶段不会标记成功。
- [x] 7.5 通过真实 MCP SSE 和 Streamable HTTP 各执行一次当前已发布 Tool；验证抓包中的 `tools/list`/`tools/call` 与手动测试报告的 Tool 名称、参数摘要、结果状态和关联标识一致。

## 8. Agent 调度与真实 Tool Trace

- [x] 8.1 增加请求作用域的 Agent Trace 模型和观察端口，覆盖会话、`tools/list`、每次 `tools/call`、Tool 响应/错误、最终回答、耗时、关联字段和 `UNOBSERVED` 状态；验证事件序列、零次/多次/并行调用单元测试。
- [x] 8.2 在 MCP Client/transport 真实事件边界接入 Trace observer，不通过最终文本或普通日志推断调用；验证观测端故障不会改变正式 Tool 结果，无法观测的必需事件会标记 `UNOBSERVED`。
- [x] 8.3 新增 Agent 调度 Case 和管理 HTTP 入口，复用当前已填充 AI、Gateway、MCP 类型、鉴权引用和超时；验证响应包含 agentTestId、事件数组、最终回答、脱敏 JSON 和稳定错误映射。
- [ ] 8.4 覆盖 Agent 零次 Tool、单次 Tool、多次/并行 Tool、Tool 错误、模型错误、MCP 鉴权失败、超时和取消；验证未发生的调用不出现在报告，已发生事件在失败报告中保留并释放客户端资源。
- [x] 8.5 使用当前已填充 AI 配置执行自然语言任务；验证 Agent 自动发现/选择 Tool、发出真实 `tools/call`、使用 Tool 结果形成最终回答，且报告与 MCP 网络记录逐项对应。

## 9. 统一测试中心前端工作区

- [x] 9.1 将现有 `gateway-test` 入口重组为统一测试中心，提供模板预检、Tool 手动调用、Agent 调度三个模式并移除绑定 CRUD、错误卡片和无关右上角搜索；验证导航、模式 DOM 和无操作入口。
- [x] 9.2 实现 Tool 目录加载、Tool 切换和 schema 驱动字段；验证页面字段、必填/敏感标记和服务端 Tool schema 一致，停用/解绑 Tool 不可执行。
- [x] 9.3 将参数工作区扩展为至少 36 个参数的搜索、全部/未填写/错误筛选、分组折叠、完成度、窗口化滚动、左栏收拢和固定 CTA；验证搜索/填写/折叠/收拢/恢复不丢值且无水平溢出。
- [x] 9.4 实现 Agent 模式自然语言任务、运行约束、真实 pending/取消和自动 Trace 时间线；验证没有 Tool 选择器，零次/多次/错误事件严格按服务端报告展示。
- [x] 9.5 统一 idle/running/success/failure、请求 JSON、适用 SQL/绑定、Tool/Agent Trace、响应 JSON 和执行日志视图；验证 JSON 来自服务端、敏感值已脱敏、接口不可用不显示成功。
- [x] 9.6 修复输入框/下拉框/聚焦/错误/禁用态样式，移除绿色边框等默认视觉覆盖，补齐键盘导航、可见 label、复制反馈和窄屏适配；验证四种交互态截图与无障碍检查通过。

## 10. 合并后的真实验收与回归

- [x] 10.1 用真实应用和当前已填充控制库执行模板预检、Tool 手动调用和 Agent 调度；验证三种模式的 request payload、响应 JSON、阶段/Trace、queryId/agentTestId 与页面逐字段一致。
- [x] 10.2 对同一模板、同一参数分别运行模板预检、管理端 Tool 手动测试和正式 MCP `tools/call`；验证列、行、截断、参数错误、只读治理和资源释放语义一致。
- [ ] 10.3 执行缺失/多余/类型错误参数、停用 Tool、危险 SQL、数据源不可用、MCP 鉴权失败、Agent 无需 Tool、Tool 错误、超时和取消矩阵；验证稳定错误、失败阶段、无伪成功和无敏感信息泄露。
- [ ] 10.4 在宽屏、窄屏、36+ 参数、Tool 单/多调用、Agent 零/多调用和失败态下截图；验证统一入口、左栏收拢、Trace、原始 JSON、固定 CTA、错误卡片移除和样式修复符合设计。
- [x] 10.5 执行 `mvn test`、`git diff --check` 和 `openspec validate mysql-template-test-console-execution --type change --strict`；验证既有模板任务、MCP `tools/list`/`tools/call`、新增 Tool/Agent 测试和全量构建均通过后，整理脱敏验收证据供 Xerina 评审。
