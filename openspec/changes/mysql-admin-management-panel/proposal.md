## Why

MySQL 模板的 SQL 执行、数据源解析和 MCP Tool 调用链路已经闭环，但管理员仍无法通过系统入口维护数据源、SQL 模板（`mcp_protocol_mysql`）和 Gateway Tool 绑定，只能依赖控制库脚本或开发夹具。现在持久化模型和领域生命周期已经具备，下一步需要补齐安全、可审计且可供 UI 使用的 REST 管理面。

## What Changes

- 新增 MySQL 数据源管理能力：支持分页/列表、详情、新建、编辑、删除和启停；创建默认停用，密码仅支持写入不回显，响应不得暴露密文、nonce、密钥或完整敏感连接信息；被协议引用的数据源删除必须被拒绝并返回稳定错误。
- 新增 MySQL 模板管理能力：以 `mcp_protocol_mysql` 作为模板记录，支持分页/列表、详情、新建、编辑、删除和启停；保存和更新继续执行 SQL 语法、只读策略、参数映射及资源上限校验，启用记录的 SQL、数据源或参数契约不可原地覆盖，变更必须遵循新协议记录/重新绑定的生命周期规则。
- 新增 Gateway Tool 与 MySQL 模板绑定管理能力：支持按 Gateway 查询、创建、修改、删除和启停绑定，维护工具名称、描述、版本、协议类型和协议 ID，并校验 Gateway、MySQL 协议及数据源关系；新绑定默认停用，绑定状态必须参与 `tools/list` 与 `tools/call` 的失败关闭逻辑。
- 为上述三类资源提供管理员 UI：在 `docs/dev-ops/nginx/html` 现有静态管理站点中复用 Bootstrap、jQuery、`admin.html` 侧边栏和动态 View 机制，新增数据源、MySQL 模板、MySQL 绑定页面及 API 配置；页面支持列表搜索与分页、表单新建/编辑、关联资源选择、状态切换、删除确认、校验/冲突错误展示和敏感字段遮罩，UI 只消费管理 REST，不直接访问控制库。
- 按 `Trigger/API → Case → Domain ← Infrastructure` 补齐 DTO、管理用例、领域管理服务、Repository/DAO 查询与写入端口，复用现有控制面表、SQL 安全责任链、异常契约和 MCP 执行链路，不另建模板语义表或旁路内存 Registry。
- 增加 REST 契约、领域规则、持久化边界、状态切换、引用完整性、敏感信息隔离和 UI 关键交互的自动化验证。

## Capabilities

### New Capabilities

- `mysql-admin-management`: 面向管理员的 MySQL 数据源、SQL 模板和 Gateway Tool 绑定的 REST 与 UI 管理入口。

### Modified Capabilities

<!-- 本变更不修改现有 MCP 执行能力的需求契约；运行链路继续复用现有规格。 -->

## Impact

- `ai-mcp-gateway-api`、`ai-mcp-gateway-trigger`、`ai-mcp-gateway-case`、`ai-mcp-gateway-domain` 和 `ai-mcp-gateway-infrastructure` 将新增管理 DTO、用例编排、领域端口/服务、Repository/DAO 及 MyBatis 查询；现有 `/admin/` 管理入口需要与 MySQL 资源管理契约衔接。
- 默认复用 `mcp_datasource`、`mcp_protocol_mysql`、`mcp_gateway_tool` 和 `mcp_protocol_mapping` 现有结构，不新增表，也不改变 `data_warehouse`；若实现过程中发现必须变更表结构、约束或索引，需先单独确认业务含义、兼容性和迁移方案。
- REST 响应沿用项目统一 `Response`/`ResponsePage` 结构，并将领域/持久化异常映射为稳定错误码；日志、异常和 UI 均不得泄露密码、密钥、Authorization、密文或完整敏感 JDBC 信息。
- 当前前端不是完整工程，而是由 Nginx 托管的静态 HTML/CSS/JavaScript 页面；本变更先在 `docs/dev-ops/nginx/html` 内增量扩展，不引入新的前端框架、打包链或独立部署单元，后续若迁移到完整前端工程需另立变更。
- 产品图与页面布局直接复刻 `docs/product/img.png`，全量页面 PNG、纯背景稿和可编辑原型位于 `evidence/ui-design/reference-replica/`，实现阶段应沿用该目录确定的构图和视觉层级。
- 本变更聚焦管理 CRUD 和生命周期，不引入管理员登录、RBAC、审批流、目标库写操作或保存时执行 SQL；这些能力如需纳入，应另立变更或先补充范围确认。
