## Context

当前 MySQL 查询链路已经能够从控制库加载数据源、模板和 Tool 绑定并完成执行，但管理员入口只覆盖通用 Gateway、HTTP 协议和认证配置。`mcp_datasource`、`mcp_protocol_mysql`、`mcp_gateway_tool` 与 `mcp_protocol_mapping` 已存在并被运行时使用，本变更不引入新表，也不改变现有数据仓库。

项目采用 `Trigger/API → Case → Domain ← Infrastructure` 的六边形分层。管理面需要同时协调数据源、MySQL 模板和 Gateway Tool 三类生命周期，因此不能把 DAO 查询或跨聚合校验直接放入 Controller。现有静态前端由 Nginx 托管，继续复用 `admin.html` 的动态 View 加载方式。

页面视觉以 `docs/product/img.png` 为产品基准，复刻证据位于 `evidence/ui-design/reference-replica/`，包括深空背景、左下角星球、右上角轨道光线、顶部 MCP Gateway 视觉区、指标卡、注册表和右侧详情检查器。

## Goals / Non-Goals

**Goals:**

- 为三类 MySQL 管理资源提供统一的分页、详情、写入、状态切换和删除契约。
- 将 SQL 安全校验、状态生命周期、引用完整性和运行时可见性收敛到领域/用例边界。
- 复用现有控制库表、DAO 和 MySQL 执行安全责任链，不创建旁路 Registry。
- 让静态 UI 通过管理 REST 完成完整 CRUD，敏感字段默认隔离，错误可定位。
- 让产品图与实现页面共享同一套版式：左侧导航、顶部视觉区、指标卡、左列表右检查器。

**Non-Goals:**

- 不新增或修改 MySQL 表、字段、索引、外键和迁移脚本。
- 不引入管理员登录、RBAC、审批流、审计中心或目标库写操作。
- 不把 SQL 保存动作变成真实业务查询，不在前端直连控制库。
- 不引入新的前端框架、打包工具或独立部署单元。

## Decisions

### 1. 以一个管理用例编排三类资源，领域规则按聚合边界分开

新增面向管理员的 Case 层编排入口，按资源类型拆分命令和查询结果，但由同一管理用例协调跨资源校验、事务边界和引用计数。Domain 只负责状态转换、唯一性、启用条件、SQL 安全规则和生命周期不变量；Case 负责将数据源、模板、Gateway 和绑定的查询组合成 UI 所需结果。

替代方案：在 `AdminController` 中直接调用 DAO。该方案会把跨表校验、敏感字段过滤和业务状态机扩散到 Trigger，无法复用到未来的 CLI 或自动化入口，因此不采用。

### 2. 拆分 MySQL 管理 API 契约与控制器，保持统一 Response/ResponsePage

MySQL 管理能力使用独立的 `IAdminMysqlService` API 契约和 `AdminMysqlController` HTTP 适配器，通用 `IAdminService` 与 `AdminController` 只保留 Gateway、HTTP 协议、认证和测试入口。两个控制器继续共享 `/admin/` 路由前缀与统一响应结构，确保静态前端无需修改接口地址：

| 资源 | 查询 | 写入/状态 |
| --- | --- | --- |
| 数据源 | `query_mysql_datasource_page`、`query_mysql_datasource_detail` | `save_mysql_datasource`、`change_mysql_datasource_status`、`delete_mysql_datasource` |
| SQL 模板 | `query_mysql_template_page`、`query_mysql_template_detail` | `save_mysql_template`、`change_mysql_template_status`、`delete_mysql_template` |
| Tool 绑定 | `query_mysql_binding_page`、`query_mysql_binding_detail` | `save_mysql_binding`、`change_mysql_binding_status`、`delete_mysql_binding` |

所有写入接口返回脱敏后的资源 DTO；密码字段只出现在请求对象，不出现在响应对象。错误响应沿用现有统一响应结构，并用稳定错误码区分参数错误、唯一性冲突、引用冲突、状态冲突和 SQL 安全拒绝。

`AdminMysqlController` 只负责请求参数适配、调用 `IAdminMysqlManageService`、DTO 转换与安全错误响应，不持有 DAO、Repository 或领域规则。替代方案是在通用 `AdminController` 中继续堆叠 MySQL 端点，或为三类资源创建新的 REST 前缀和响应包装；前者会混合不同管理边界并持续扩大单类职责，后者会造成前端同时维护两套路由契约，因此均不采用。

### 3. Repository 只做持久化映射，状态机和安全规则留在 Domain

在 Domain 定义三类管理 Repository 的最小读写端口；Infrastructure 在 `adapter/repository` 中复用现有 DAO/PO 映射并补充分页、引用计数、按 Gateway 查询和安全字段更新。Repository 不负责启用/停用策略，不在 SQL Mapper 中判断模板是否可覆盖。

MySQL 模板保存继续调用已有 SQL 语法、只读策略、参数映射和资源上限校验端口。启用模板的 SQL、数据源或参数契约变更由 Domain 拒绝；停用模板修改后仍需重新执行同一安全责任链。

替代方案：在 Infrastructure 中通过 SQL 条件隐式实现生命周期限制。该方案无法保证内存用例、批量接口和未来存储实现的一致性，因此不采用。

### 4. 使用引用完整性查询拒绝删除，不新增物理外键

数据源删除前由 Case 查询 MySQL 模板和绑定引用数量；模板删除前查询绑定关系；Gateway Tool 绑定删除使用现有逻辑关联。错误返回引用资源摘要，便于 UI 给出可操作提示。现有 `protocol_type/protocol_id` 逻辑关联继续保留，不为管理功能强行增加跨协议物理外键。

### 5. 状态变更采用原子命令并同步运行时可见性

数据源、模板和绑定的状态切换均使用带当前状态条件的原子更新；成功后返回新状态。停用数据源不得被新模板启用或新绑定发布使用；停用绑定立即从对应 Gateway 的 `tools/list` 和 `tools/call` 路径中排除。执行链路继续从当前绑定关系读取，不引入内存兜底 Registry。

### 6. 前端采用三页资源工作台和共享设计 Token

在 `docs/dev-ops/nginx/html/views` 新增数据源、SQL 模板、Tool 绑定 View，在 `js/config.js` 增加接口常量，在 `js/app.js` 复用列表、分页、表单、Toast 和确认交互。页面结构固定为：

1. 参考图复刻背景与左侧导航。
2. 顶部面包屑、全局搜索、用户上下文。
3. 业务标题、说明、MCP Gateway 连接图和四张指标卡。
4. 左侧注册表/筛选区，右侧详情检查器。
5. 新建/编辑采用抽屉或局部工作区，避免嵌套 Bootstrap 大弹窗。

产品图和原型文件以 `reference-replica/admin-layout-replica.html` 为基准，各页面 PNG 作为 OpenSpec 视觉证据，不直接作为运行时代码依赖。

### 7. 以字段级脱敏和日志过滤作为安全边界

请求 DTO 与响应 DTO 分离；数据源密码只进入写入命令，查询、详情、异常和日志统一过滤密码、Token、Authorization、密文、nonce、完整 JDBC URL。更新请求中密码为空代表保持原值，密码非空才触发重新加密和持久化。

## Risks / Trade-offs

- **[现有 DAO 缺少分页或引用计数查询] →** 先补充只读查询和映射测试，再接入管理 Case；不通过修改表结构解决。
- **[逻辑关联数据存在历史脏数据] →** 创建、启用和绑定操作统一做存在性与状态校验；查询列表保留“关联异常”状态，避免静默修复生产数据。
- **[启用模板不可变规则影响管理员习惯] →** UI 在编辑页明确区分展示字段和执行语义字段，冲突时提示“停用后修改或创建新模板”。
- **[静态前端重复实现页面状态] →** 抽取现有 `app.js` 的分页、Toast、确认和错误映射函数，资源页只提供字段配置和行渲染。
- **[产品背景图与前景文字对比不足] →** 使用参考图复刻 PNG 进行视觉验收；标题、表格和状态标签必须满足可读对比度，必要时使用半透明面板而不改变背景构图。
- **[跨聚合写入出现部分成功] →** 绑定创建、启用和删除使用明确事务边界；跨资源操作失败时不返回部分成功状态，并保留稳定错误码。

## Migration Plan

1. 先在控制库只读环境验证现有表字段、状态值和逻辑关联，不执行 DDL。
2. 实现 Domain 端口、状态规则和 SQL 安全复用，并用单元测试锁定错误码。
3. 实现 Infrastructure DAO/Repository 查询映射，再实现 Case 与 API DTO。
4. 增加 Trigger 接口并用真实控制库验收分页、写入、引用冲突和敏感字段隔离。
5. 接入静态 UI，使用 `reference-replica` 产品图完成视觉和交互验收。
6. 发布顺序为后端兼容接口 → 前端资源页 → 启用状态联动验证；任一阶段失败时回滚新增代码，不回滚或改写既有控制库数据。

## Open Questions

- 现有控制库中数据源、模板和绑定的状态值是否全部使用 `0/1`，还是存在历史枚举值？实现前需要通过真实库样本确认映射，但不改变本设计的接口语义。
- 是否需要将连接健康检查做成异步刷新？首个 MVP 允许使用显式“连接测试”命令，后续可在不改变 CRUD 契约的情况下增加定时健康探测。
