# MySQL 控制面持久化决策记录

## 决策状态

方案 A 已由 Xerina 确认，并按“轻量控制面”重新收敛。数据源连接信息完整存入 Gateway 控制库；连接池和运行时技术参数继续由 App Config 管理。开发验收允许清空并重建 Gateway 控制库，但保留真实 `data_warehouse` 业务数据。

本记录定义实现边界，不是已执行的 DDL，也不代表已经修改数据库。

## 最终关系

```text
mcp_gateway_tool
  ├─ protocol_type=mysql + protocol_id ──> mcp_protocol_mysql
  │                                         └─ datasource_id ──FK──> mcp_datasource
  ├─ protocol_type=http + protocol_id ───> mcp_protocol_http
  └─ gateway_id + tool_name + description + version + status

mcp_protocol_mapping
  └─ protocol_type + protocol_id ──> HTTP/MySQL protocol request mapping
```

`mcp_mysql_template` 不再存在。`mcp_protocol_mysql` 直接保存 SQL 模板和执行策略，避免模板表再次复制 Tool 名称、描述、版本和输入 Schema。

## 数据源边界

### `mcp_datasource`

通用数据源注册表保存 `datasource_ref`、名称、`datasource_type`、`jdbc_url`、用户名、`password_ciphertext`、`password_nonce`、`encryption_key_ref`、状态和审计时间。JDBC URL 不允许携带密码参数。

连接池大小、连接获取/校验超时、最大并发等运行时技术参数仍由 App Config 保存。运行时策略取控制库业务上限和 App Config 技术上限的交集。解密主密钥不进入控制库，只能由 App Config 或外部密钥服务提供。

### `mcp_protocol_mysql`

保存唯一 `protocol_id`、`datasource_id`、`sql_text`、`max_rows`、`max_result_bytes`、`max_columns`、`timeout_ms`、状态和审计时间。`sql_text` 最大 64KB，只允许经 JSqlParser 校验的安全只读语句。

名称、描述和版本继续由既有 `mcp_gateway_tool` 提供，请求参数由既有 `mcp_protocol_mapping` 提供；MySQL mapping 以 `(protocol_type=mysql, protocol_id)` 查询。

## 既有表调整

- `mcp_gateway_tool`：增加 Tool 状态；`protocol_type` 扩展至 `VARCHAR(32)`；增加 gateway/status 和协议查询索引；工具 ID 唯一性改为 gateway 级组合键。
- `mcp_protocol_mapping`：增加 `protocol_type`，唯一键和查询全部使用 `(protocol_type, protocol_id)`；参数名保持扁平并与 SQL 命名占位符一致。
- `mcp_protocol_http`：`protocol_id` 增加唯一约束，以匹配现有 `selectOne` 读取语义。

## 保存前安全校验

```text
字段/业务语义
  -> JSqlParser
  -> 单语句、SELECT/合法 CTE
  -> 拒绝写操作、DDL、DCL、事务、锁、文件输出、危险函数
  -> 占位符与 request mapping 一一对应
  -> 长度与策略边界
  -> 持久化
```

保存阶段不执行 `EXPLAIN`、PreparedStatement 元数据查询或真实业务 SQL。表、列、权限、网络错误延迟到真实 `tools/call`，由统一异常契约安全返回。

## MCP 链路

`tools/list` 按当前 Gateway 的 `mcp_gateway_tool` 绑定查询，按协议类型分支；MySQL 必须同时满足 Tool、协议和数据源启用，并从 mapping 构造 `inputSchema`。`tools/call` 以 `(gateway_id, tool_name)` 固定解析协议和数据源，复用现有参数绑定器和执行器。禁止全局模板枚举和 Registry fallback，客户端不能提交 SQL、数据源引用、JDBC 地址或凭证。

## 状态与版本

数据源、MySQL 协议和 Tool 只有 `DISABLED` / `ENABLED` 两态，默认禁用。SQL、绑定或协议策略变化时创建新的 `protocol_id` 并重新绑定，旧记录停用但不物理删除；不再使用 draft、published、deprecated 等中间态。

## 真实验收

验收顺序为：清空 Gateway 控制库 → 创建调整后的既有表与两张新表 → 写入真实 `data_warehouse` 数据源、MySQL 协议和 Tool 绑定 → 执行 `tools/list` → 执行真实 `tools/call` → 验证参数、资源上限、非法 SQL、启停、运行时错误和敏感信息隔离。清理范围不包含 `data_warehouse` 的 DDL、DML、权限或测试数据。

## 明确不做

- 不做生产迁移、回滚、历史数据兼容和 Admin JWT/RBAC。
- 不保留 Config 固定模板、内存种子或旧 Registry 兜底。
- 不在保存阶段探测目标数据库。
- 不为每种数据库新增重复的数据源表；其他数据库协议未来复用 `mcp_datasource` 并另立协议实现。
