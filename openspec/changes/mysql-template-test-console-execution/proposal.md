## Why

当前管理端“测试运行”接口只按模板标识返回模板详情，没有接收管理员填写的参数，也没有进入已存在的 MySQL 模板查询执行链路，因此页面只能显示成功而无法验证真实绑定、只读策略、数据源连接和 MCP 结构化结果。现在需要把产品图中的多参数测试工作区落成端到端能力，使管理员能够在 Gateway 内填写参数、观察真实执行阶段，并查看可复制的原始请求与响应 JSON。

## What Changes

- 新增 SQL 模板测试执行用例：接收模板标识、版本和参数值，复用已发布模板、固定数据源、参数类型校验、只读 SQL 安全责任链、资源上限和 JDBC 查询端口，返回真实的结构化查询结果。
- 将管理测试接口从“查询模板详情”改为“执行模板测试”，建立稳定的请求/响应 DTO 契约，包含参数回显（按安全规则处理）、请求 JSON、执行阶段、耗时、行数、结果大小、截断状态、响应 JSON 和错误码。
- 增加可观测的执行阶段模型，至少覆盖参数校验、只读策略校验、获取数据源连接、执行 SQL、组装 MCP 响应；同步记录 queryId 和阶段耗时，失败时返回稳定错误码和可恢复提示。
- 将 SQL 模板管理 UI 改造成测试工作区：支持按模板契约动态生成多参数表单、搜索、分组折叠、完成度统计、收拢模板导航、固定底部操作栏和执行中/成功/失败状态。
- 在测试工作区提供“请求 JSON”“SQL 绑定”“响应 JSON”“执行日志”视图；原始数据保持 MCP Gateway 结构，不转换为业务表格、图表、CSV 或 PNG。
- 增加 Domain、Infrastructure、Trigger/API、静态前端和联调验收测试，覆盖真实执行链路、错误边界、敏感信息隔离、UI 请求契约和端到端响应展示。

## Capabilities

### New Capabilities

- `mysql-template-test-console`: 管理员通过参数化 SQL 模板测试工作区执行查询，并查看执行阶段及原始 MCP 请求/响应。

### Modified Capabilities

- `mysql-readonly-template-query`: 管理端测试执行必须调用同一已发布模板查询链路，并返回受资源治理约束的结构化结果。
- `mysql-query-governance`: 管理端测试请求必须遵守与 MCP Tool 调用相同的参数绑定、只读策略、资源限制、失败关闭和敏感信息隔离规则。

## Impact

- API/Trigger：`MysqlAdminTestRequestDTO`、`IAdminMysqlService`、`AdminMysqlController` 及统一响应错误映射。
- Case/Domain：新增测试执行用例编排，扩展 `MysqlTemplateQueryService` 的阶段结果/错误语义，复用现有 `MysqlTemplate`、`MysqlQueryCommand`、`MysqlQueryResult` 和 Domain Port。
- Infrastructure：JDBC 查询适配器的阶段计时、queryId 关联和技术异常转换；不新增表、字段、索引或控制库迁移。
- 前端：`docs/dev-ops/nginx/html/views/sql-template.html`、`js/mysql-workbench.js`、API 配置和共享样式；UI 以 `docs/product/sql-template-test-console-v3.png` 与 `sql-template-test-console-running-v3.png` 为视觉基准。
- 测试：Domain/Infrastructure/Trigger 单元测试、应用集成测试、真实 MySQL 只读执行验收、静态站点前后端联调验收。
