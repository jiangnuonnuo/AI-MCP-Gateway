## Context

当前实现的 MySQL 能力存在三个结构性问题：数据源和模板仍以 App Config/内存 Registry 为主；MySQL Tool 在 `tools/list` 与 `tools/call` 中没有严格经过 Gateway 绑定；独立模板表重复保存 MCP Tool 已有的身份字段。现有 HTTP 链路已经通过 `mcp_gateway_tool -> protocol -> mapping` 工作，因此 MySQL 只应补充协议实现，不应另造一条 Tool 链路。

实现还暴露出若干必须一并修正的查询问题：`mcp_gateway_tool` 没有状态字段，协议类型长度过短；协议查询丢失 `protocol_type` 并在会话仓储中硬编码 HTTP；MySQL 模板被全局枚举；`mcp_protocol_mapping` 没有协议类型而无法区分同值协议 ID；HTTP 协议 ID 没有唯一约束却被按单行读取。

## Goals / Non-Goals

### Goals

- 以通用数据源记录保存未来可扩展的数据库连接信息和加密凭证。
- 让 `mcp_protocol_mysql` 直接成为 SQL 模板和执行策略的协议记录，消除模板表与协议表的重复关联。
- 复用既有 Tool、Mapping、参数转换和执行器链路；业务编排和安全规则留在 Domain。
- 以 `ENABLED` / `DISABLED` 两态控制数据源、协议和 Tool 的可用性。
- 通过本地 JSqlParser 校验和真实 `data_warehouse` 只读调用完成验收。

### Non-Goals

- 不在本变更中实现生产迁移、历史兼容、Admin 认证、RBAC 或目标库写操作。
- 不在保存阶段执行 `EXPLAIN`、元数据查询或业务 SQL。
- 不修改 `data_warehouse` 结构和业务数据。

## Final Control-Plane Model

### `mcp_datasource` — 通用数据源注册表

| 字段 | 约束/用途 |
| --- | --- |
| `id` | `BIGINT UNSIGNED` 主键 |
| `datasource_ref` | `VARCHAR(64)` 唯一业务引用 |
| `datasource_name` | `VARCHAR(128)` 管理名称 |
| `datasource_type` | `VARCHAR(32)`，例如 `mysql`、`postgresql` |
| `jdbc_url` | `VARCHAR(1024)`，禁止携带密码参数 |
| `username` | `VARCHAR(128)` |
| `password_ciphertext` | `TEXT`，只保存密文 |
| `password_nonce` | `VARCHAR(64)`，每条记录独立 nonce |
| `encryption_key_ref` | `VARCHAR(128)`，外部密钥标识或版本 |
| `status` | `TINYINT(1)`，`0=DISABLED`、`1=ENABLED` |
| `created_at`/`updated_at` | 审计时间 |

连接池大小、连接获取超时、连接校验、最大并发和其他运行时参数不进入该表，继续由 App Config 管理。数据源业务上限与 App 技术上限在 Domain 合并后取更严格值。

### `mcp_protocol_mysql` — MySQL 协议和 SQL 模板

| 字段 | 约束/用途 |
| --- | --- |
| `id` | `BIGINT UNSIGNED` 主键 |
| `protocol_id` | `BIGINT UNSIGNED` 唯一逻辑协议 ID，供 `mcp_gateway_tool` 使用 |
| `datasource_id` | 外键指向 `mcp_datasource.id`，禁止级联删除 |
| `sql_text` | `MEDIUMTEXT`，Domain 最大 64KB，只允许安全只读语句 |
| `max_rows` | `INT UNSIGNED` 行数业务上限 |
| `max_result_bytes` | `BIGINT UNSIGNED` 结果字节业务上限 |
| `max_columns` | `SMALLINT UNSIGNED` 列数业务上限 |
| `timeout_ms` | `INT UNSIGNED` 查询超时业务上限 |
| `status` | `TINYINT(1)`，`0=DISABLED`、`1=ENABLED` |
| `created_at`/`updated_at` | 审计时间 |

名称、描述、版本和输入 Schema 继续使用既有 `mcp_gateway_tool`、`mcp_protocol_mapping` 的标准字段，避免再次复制一份“模板元数据”表。若 SQL 或绑定需要变更，创建新的 `protocol_id` 记录并重新绑定 Tool；不原地覆盖启用记录。

### Existing tables to adjust

`mcp_gateway_tool` 保留 `tool_name`、`tool_description`、`tool_version` 作为对外 MCP 身份，新增 `status`，将 `protocol_type` 扩展为 `VARCHAR(32)`，增加 `(gateway_id,status)` 和 `(protocol_type,protocol_id)` 索引；工具 ID 的唯一性调整为 gateway 级 `(gateway_id,tool_id)`，因为该表描述的是 Gateway 绑定而非全局注册表。

`mcp_protocol_mapping` 新增 `protocol_type VARCHAR(32)`，所有唯一键和查询均按 `(protocol_type,protocol_id)` 解释协议；请求参数名称必须与 SQL 命名占位符一致，保持现有扁平参数绑定。`mcp_protocol_http.protocol_id` 增加唯一约束，以匹配现有单行读取语义。

## Relationship

```text
mcp_gateway_tool
  ├─ protocol_type=mysql, protocol_id ──(logical)──> mcp_protocol_mysql
  │                                                   ├─ datasource_id ──(FK)──> mcp_datasource
  │                                                   └─ SQL + limits + status
  ├─ protocol_type=http, protocol_id ───(logical)──> mcp_protocol_http
  └─ gateway_id + tool_name + description + version + status

mcp_protocol_mapping
  └─ (protocol_type, protocol_id) ──(logical)──> http/mysql protocol
     └─ request mapping -> existing input schema / parameter binder
```

`mcp_gateway_tool.protocol_id` 是多协议逻辑关联，不能在数据库层建立一个同时指向 HTTP 和 MySQL 的多态外键；同协议内部的 `protocol_id`、数据源外键和唯一键仍使用数据库约束保证完整性。

## Runtime Flow

### `tools/list`

1. 按请求 Gateway 查询启用的 `mcp_gateway_tool`。
2. 按 `protocol_type` 分支；MySQL 只连接到启用的 `mcp_protocol_mysql` 和 `mcp_datasource`。
3. 以既有 `tool_name`、`tool_description`、`tool_version` 生成 MCP Tool；从 `(mysql, protocol_id)` 查询 request mapping 生成 `inputSchema`。
4. 只返回标准 `name`、`description`、`inputSchema`，不返回 SQL、JDBC URL、用户名、密文或内部 ID。

### `tools/call`

1. 以 `(gateway_id, tool_name)` 查询完整 Tool 行，保留 `protocol_type`。
2. MySQL 分支查询协议和数据源，合并两者状态与 App Config 的技术上限。
3. 使用既有 `MysqlTemplateParameterBinder` 和 `MysqlTemplateExecutor`；客户端只能提交声明参数，不能覆盖 SQL、数据源或凭证。
4. 目标库的表/列/权限/网络错误在调用阶段转换为统一、安全的异常，不改变控制面状态。

禁止全局枚举模板，禁止 `mysqlTemplateRegistry` 作为持久化数据缺失时的兜底。

## Domain / Infrastructure / App Boundaries

- Domain：数据源和协议生命周期、绑定编排、两态规则、SQL JSqlParser 责任链、参数契约和策略合并。
- Infrastructure：MyBatis/JDBC PO、Mapper、DAO、Repository、密文加解密适配器、数据库异常转换；不得承载业务规则。
- App Config：控制库连接、连接池、运行时技术上限和密钥解析器；由 `@Resource` / `@Bean` 进行 IoC 装配，不使用 Spring Bean 的手工 `final` 构造注入。
- Case/API/Trigger：管理用例和 MCP 请求编排，复用现有 Tool 路由，不新建平行执行链。

## Safety and Lifecycle

保存 SQL 的责任链为：字段/语义校验 → JSqlParser 完整解析 → 单语句 → SELECT/合法 CTE → 拒绝写操作、DDL、DCL、事务、锁和文件输出 → 危险函数检查 → 命名参数与 mapping 一一对应 → 长度与策略边界 → 持久化。保存阶段不访问目标数据库。

状态只有 `DISABLED` 和 `ENABLED`；默认禁用、失败关闭。启用内容发生变化时产生新的协议记录并重新绑定，旧记录停用但不物理删除。

## Development Acceptance

开发阶段清空并重建 Gateway 控制库，重新写入 HTTP 基础数据和真实 `data_warehouse` 数据源/协议/Tool 绑定；不得清理或修改 `data_warehouse`。验收覆盖 `tools/list`、真实 `tools/call`、参数与资源上限、非法 SQL 保存拒绝、运行时表/列/权限错误、启停和敏感信息隔离。

## Risks / Trade-offs

- 密文入库增加密钥管理责任，但能满足多数据源和重启恢复；主密钥必须留在 App Config 或外部密钥服务。
- 不做保存时目标库探测会将表、列和权限错误延后到真实调用，但避免了保存阶段副作用和跨库依赖。
- 逻辑协议关联不能用单一多态 FK，依靠协议类型、唯一索引和 Repository 查询保证一致性。
- 通用数据源表为未来数据库类型预留空间，但 MySQL 协议只处理 `datasource_type=mysql`，其他引擎另立协议实现。
