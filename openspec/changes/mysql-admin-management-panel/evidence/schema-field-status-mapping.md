# MySQL 控制面字段与状态映射

核对来源：`docs/dev-ops/mysql/sql/ai_mcp_gateway_v2.sql`（同内容的开发环境分发脚本保持一致）。本变更只复用现有控制库结构，不执行或修改 DDL。

| 资源 | 关键字段 | 状态语义 | 逻辑关联 |
| --- | --- | --- | --- |
| `mcp_datasource` | `id`、`datasource_ref`、`datasource_name`、`datasource_type`、`jdbc_url`、`username`、`password_ciphertext`、`password_nonce`、`encryption_key_ref`、`status` | `status=0` 停用，`status=1` 启用；新建由管理领域默认停用 | `datasource_ref` 被 `mcp_protocol_mysql.datasource_id` 通过物理外键引用 |
| `mcp_protocol_mysql` | `protocol_id`、`datasource_id`、`sql_text`、`max_rows`、`max_result_bytes`、`max_columns`、`timeout_ms`、`status` | `status=0` 停用，`status=1` 启用；协议启用前数据源必须启用 | `datasource_id -> mcp_datasource.id`；参数契约通过 `mcp_protocol_mapping` 的 `protocol_type=mysql` 逻辑关联 |
| `mcp_gateway_tool` | `gateway_id`、`tool_id`、`tool_name`、`tool_type`、`tool_description`、`tool_version`、`protocol_id`、`protocol_type`、`status` | `status=0` 停用，`status=1` 启用；新建绑定默认停用 | `protocol_type=mysql` 且 `protocol_id` 指向 `mcp_protocol_mysql.protocol_id`；`gateway_id` 指向 Gateway 配置 |
| `mcp_protocol_mapping` | `protocol_type`、`protocol_id`、`mapping_type`、`field_name`、`mcp_path`、`mcp_type`、`is_required`、`sort_order` | 无独立发布状态，随协议状态参与运行时可见性 | `(protocol_type, protocol_id)` 区分 HTTP/MySQL 协议；MySQL 请求参数使用 `mapping_type=request` |

## 安全字段边界

`password_ciphertext`、`password_nonce`、`encryption_key_ref` 只允许在 Infrastructure 写入和运行时解密路径使用，查询 DTO、日志和异常不得返回。`jdbc_url` 管理响应只返回脱敏摘要，不返回凭证参数或完整敏感连接信息。

## 结论

现有字段、状态约束、唯一键、索引、外键和逻辑关联已经满足本管理变更；实现不新增表、字段、索引、约束，也不修改 `data_warehouse`。
