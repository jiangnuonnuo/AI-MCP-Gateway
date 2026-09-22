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
- [ ] 6.5 执行完整验收命令 `mvn test`、`git diff --check`、`openspec validate --change mysql-template-test-console-execution --strict`，并记录真实 MySQL/浏览器联调证据；只有所有分层测试和端到端测试通过才标记变更完成。
