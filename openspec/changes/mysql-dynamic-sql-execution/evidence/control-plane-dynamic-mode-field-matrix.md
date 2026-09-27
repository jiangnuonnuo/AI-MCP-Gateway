# MySQL 动态执行模式控制面字段矩阵

## 业务规则

`mcp_protocol_mysql` 继续作为 MySQL Tool 的协议配置记录。`execution_mode` 决定 SQL 来源：

- `TEMPLATE`：SQL 来自 `sql_text`，请求只允许模板声明的参数；
- `DYNAMIC_READONLY`：SQL 来自本次 MCP `tools/call` 的 `arguments.sql`，协议记录只提供固定数据源、只读状态和资源上限。

动态模式仍然必须绑定 `mcp_datasource`，不能由调用方在请求中选择数据源。

## 字段矩阵

| 表 | 字段 | 调整 | 兼容与约束 |
| --- | --- | --- | --- |
| `mcp_protocol_mysql` | `execution_mode` | 新增 `VARCHAR(32) NOT NULL DEFAULT 'TEMPLATE'` | 允许 `TEMPLATE`、`DYNAMIC_READONLY`；旧记录回填 `TEMPLATE`；未知值失败关闭 |
| `mcp_protocol_mysql` | `sql_text` | 允许 `NULL` | `TEMPLATE` 必须非空；`DYNAMIC_READONLY` 必须为空；通过检查约束表达 |
| `mcp_protocol_mysql` | `datasource_id` | 保持不变 | 模板与动态模式均必须引用启用的数据源 |
| `mcp_protocol_mysql` | `max_rows`、`max_result_bytes`、`max_columns`、`timeout_ms` | 保持不变 | 两种模式均使用服务端上限 |
| `mcp_gateway_tool` | `protocol_id`、`protocol_type`、`gateway_id` | 保持不变 | 动态 Tool 仍按 Gateway 绑定，不创建全局特殊 Tool |
| `mcp_protocol_mapping` | 动态模式请求映射 | 动态模式不读取模板参数白名单 | 动态 Schema 由执行模式生成；模板 mapping 行不参与动态参数校验 |

`execution_mode` 不新增独立索引：运行时始终先按 `protocol_id` 解析 Gateway 绑定，模式只在已定位的协议记录上做分支；现有 `uq_mysql_protocol_id` 和 `idx_mysql_protocol_status` 保持不变。

## 迁移与回滚

迁移顺序：新增字段并回填 `TEMPLATE`，再放宽 `sql_text` 可空性和增加模式检查约束；现有模板数据、数据源外键、Tool 逻辑关联和状态值不变。动态协议记录只能在动态模式字段落定后创建。

回滚顺序：停用动态协议记录，恢复所有协议为 `TEMPLATE`，删除模式检查约束和字段，最后恢复 `sql_text NOT NULL`。回滚不得删除模板、Tool 绑定或数据源记录。

## 验收依据

- PO、Mapper、Repository 和运行时 VO 能读写 `execution_mode`；
- 模板记录不传 SQL 时无法启用；
- 动态记录不依赖服务端 SQL 正文但必须有数据源和策略；
- 新专用 Gateway 的 Tool 绑定只能解析当前 Gateway 的动态协议记录。
