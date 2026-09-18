## Why

当前 MySQL 执行链路已经可以在进程内运行，但数据源、SQL 模板和 Gateway Tool 绑定仍然分散在 App Config、固定初始化代码和内存 Registry 中。这样无法管理多个真实数据源，也无法保证 `tools/list` 与 `tools/call` 使用同一条持久化绑定链路。现有独立模板表还重复保存 MCP Tool 已有的名称、描述和 Schema，导致控制面模型过重。

## What Changes

- 以通用 `mcp_datasource` 保存所有数据库数据源的连接信息和加密凭证，不再使用只面向 MySQL 的数据源表名。
- 让 `mcp_protocol_mysql` 直接保存 SQL 模板、执行策略和数据源外键，移除独立的 `mcp_mysql_template`。
- 为既有 `mcp_gateway_tool` 增加状态、完整协议类型和 gateway 级工具唯一性，使 MySQL 协议通过现有 Tool 绑定接入。
- 为既有 `mcp_protocol_mapping` 增加协议类型，避免 HTTP/MySQL 协议 ID 冲突；补齐 HTTP 协议唯一约束。
- 保持连接池、连接超时、并发等运行时技术参数在 App Config；控制库只保存业务数据源和查询策略。
- 使用 JSqlParser 和现有 Domain 责任链在 SQL 保存前拒绝危险语句；不执行 `EXPLAIN` 或目标数据库元数据查询。
- 复用现有 Tool 转换、参数绑定和 MySQL 执行链路；`tools/list`、`tools/call` 只解析当前 Gateway 的持久化绑定，不再遍历全局模板或使用 Registry 兜底。
- 开发验收允许清空并重建 Gateway 控制库，但保留真实 `data_warehouse` 业务数据，执行真实只读 MySQL 调用。

## Capabilities

- `mysql-control-plane-persistence`：持久化通用数据源、MySQL SQL 协议和 Gateway Tool 绑定，并完成安全校验与真实调用验收。

## Impact

- 数据库：新增 `mcp_datasource`、`mcp_protocol_mysql`，修改既有 `mcp_gateway_tool`、`mcp_protocol_mapping`、`mcp_protocol_http` 的约束和索引。
- Domain：保留业务编排、生命周期、SQL 安全责任链和策略合并，删除固定模板/发布态语义。
- Infrastructure：实现 PO、Mapper、Repository、数据源凭证解密和控制库查询；不新增独立 Infrastructure 异常包。
- App/Case：通过 IoC 装配控制库连接池和密钥解析器，复用现有 MCP Tool 路由。
- 测试：真实目标库只读验收，测试文件统一位于 `ai-mcp-gateway-app/src/test/java/cn/bugstack/ai/test`。

## Non-Goals

- 不修改 `data_warehouse` 的表结构、业务数据或权限。
- 不设计生产环境迁移、回滚、备份窗口、Admin 认证、RBAC 或审批流程。
- 不在保存阶段执行 SQL，不提供动态 SQL、写操作或异步执行能力。
- 不新增独立的模板语义表；现有 `mcp_gateway_tool` 的 MCP 名称、描述和版本继续作为对外身份信息。

## Development Reset

本变更处于开发阶段，采用清空并重建 Gateway 控制库的方式验证最终结构。清理范围仅包含控制库，不包含 `data_warehouse`。生产迁移必须另立 OpenSpec 任务。
