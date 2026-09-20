## 1. 现状核对与管理契约

- [x] 1.1 核对 `mcp_datasource`、`mcp_protocol_mysql`、`mcp_gateway_tool`、`mcp_protocol_mapping` 的现有字段、状态值和逻辑关联，输出字段/状态映射记录并确认本变更不需要 DDL
- [x] 1.2 对照 `IAdminMysqlService`、`AdminMysqlController`、`Response`、`ResponsePage` 整理数据源、SQL 模板和绑定的请求/响应 DTO、错误码和接口矩阵，完成 API 编译检查
- [x] 1.3 将敏感字段清单和日志过滤规则写入测试夹具，验证密码、Token、Authorization、密文、nonce 和完整 JDBC 信息不会进入响应或日志

## 2. Domain 规则与失败测试

- [x] 2.1 为数据源新建、编辑、删除、启停和引用冲突编写失败测试，覆盖默认停用、密码省略保持原值和 `DATASOURCE_IN_USE` 错误
- [x] 2.2 实现数据源管理命令、状态转换和引用完整性领域规则，使 2.1 的测试通过
- [x] 2.3 为 MySQL 模板 SQL 安全校验、参数契约、资源上限和启用后不可原地覆盖编写失败测试，覆盖 `ENABLED_TEMPLATE_IMMUTABLE`
- [x] 2.4 实现模板管理命令和生命周期规则，复用现有只读 SQL 安全责任链并使 2.3 的测试通过
- [x] 2.5 为 Gateway Tool 绑定唯一性、关联资源状态和启停对运行时可见性的影响编写失败测试，覆盖重复工具名称和停用绑定
- [x] 2.6 实现绑定管理命令、关系校验和状态规则，使 2.5 的测试通过

## 3. Infrastructure 持久化适配

- [x] 3.1 在现有 DAO/Mapper 上补充三类资源的分页、详情、引用计数、按 Gateway 查询和状态更新查询，使用真实字段映射并通过 Mapper 单元测试
- [x] 3.2 实现数据源、MySQL 模板和绑定的 Domain Repository 适配器，验证查询结果、状态值和逻辑关联映射与 Domain 模型一致
- [x] 3.3 实现敏感字段的写入/更新隔离，验证密码非空才覆盖原值、查询不会回读密文、技术异常已转换为统一错误码
- [x] 3.4 为启用/停用、删除前引用检查和绑定状态读取增加事务边界测试，验证失败时不会出现部分写入

## 4. Case 与 REST Trigger

- [x] 4.1 实现 MySQL 管理 Case 的分页、详情、新建、编辑、删除和状态切换用例，验证跨资源校验只在 Case/Domain 编排而不下沉到 Controller
- [x] 4.2 新增独立的 `IAdminMysqlService` 和 `AdminMysqlController`，从通用 `IAdminService`、`AdminController` 中移出全部 MySQL 管理端点，同时保持 `/admin/` 路由和统一 `Response`/`ResponsePage` 契约不变
- [x] 4.3 增加稳定错误码到安全 HTTP 响应的映射，验证字段级校验、唯一性冲突、引用冲突、状态冲突和 SQL 安全拒绝均不泄露堆栈或敏感内容
- [x] 4.4 用 Mock Repository 完成 Controller/Case 单元测试，验证默认停用、启用限制、绑定运行时可见性和分页参数边界

## 5. 静态管理前端

- [x] 5.1 在 `docs/dev-ops/nginx/html` 增加数据源、SQL 模板和 Tool 绑定导航入口、View 文件和 API 配置，并验证现有动态 View 加载不回归
- [x] 5.2 实现数据源工作台：列表搜索/分页、详情检查器、新建/编辑表单、密码遮罩、启停、连接测试、删除确认和引用冲突提示
- [x] 5.3 实现 SQL 模板工作台：模板筛选、SQL 编辑器、参数契约、执行护栏、数据源选择、启用后只读字段保护、校验错误和测试运行入口
- [x] 5.4 实现 Tool 绑定工作台：按 Gateway 筛选、模板/数据源关联选择、工具名称唯一性提示、启停、删除确认和绑定关系预览
- [x] 5.5 抽取共享分页、Toast、错误映射、确认弹窗和脱敏渲染逻辑，验证 UI 只调用 REST API，不访问控制库
- [x] 5.6 按 `evidence/ui-design/reference-replica/` 产品图复刻页面视觉，验证左下角星球、右上角轨道背景、顶部 MCP Gateway 视觉区、指标卡和左表右详情布局一致
- [x] 5.7 按登录页参考图重构现有静态登录入口，完成深空品牌视觉、能力说明、玻璃拟态表单、中英文切换、密码可见性、错误反馈和响应式布局，并验证本地账号登录跳转不回归且不引入真实认证或 RBAC

## 6. 集成与验收

- [x] 6.1 增加应用模块集成测试，使用真实或容器化控制库验证三类资源 CRUD、分页、状态切换、引用完整性和敏感字段隔离
- [x] 6.2 扩展 MySQL 控制面 acceptance 测试，验证停用绑定不出现在 `tools/list` 且不能被 `tools/call` 调用，启用路径继续执行原有 SQL 安全链路
- [x] 6.3 在静态站点中按数据源 → SQL 模板 → Tool 绑定顺序完成端到端 UI 验收，记录网络请求、错误提示和刷新后的状态一致性
- [x] 6.4 执行 `mvn test`、`git diff --check` 和 `openspec validate --change mysql-admin-management-panel --strict`，确认测试、编码、规格和任务格式全部通过
