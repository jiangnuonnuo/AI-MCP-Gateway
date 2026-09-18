## 1. Schema freeze and development reset

- [x] 1.1 冻结 `mcp_datasource`、`mcp_protocol_mysql` 的字段、类型、约束、索引和外键，并同步 ER 图、规格和决策记录。
- [x] 1.2 删除独立 `mcp_mysql_template` 设计；将 SQL、数据源绑定和查询上限归入 `mcp_protocol_mysql`。
- [x] 1.3 修改既有 `mcp_gateway_tool`：增加 `status`，扩展 `protocol_type`，调整 gateway 级工具唯一键并增加查询索引。
- [x] 1.4 修改既有 `mcp_protocol_mapping`：增加 `protocol_type`，按 `(protocol_type,protocol_id)` 建立唯一键和查询索引。
- [x] 1.5 为 `mcp_protocol_http.protocol_id` 增加唯一约束，保持现有单行读取语义。
- [x] 1.6 编写仅供开发环境使用的清库重建顺序；确认只清理 Gateway 控制库，不触碰 `data_warehouse`。

## 2. Control database implementation

- [x] 2.1 清空并重建开发 Gateway 控制库，创建新数据源和 MySQL 协议表以及调整后的既有表。
- [x] 2.2 写入 HTTP 回归基础数据，确保 HTTP Tool 不依赖 MySQL 表。
- [x] 2.3 写入真实 `data_warehouse` 数据源记录、MySQL 协议 SQL 和 Gateway Tool 绑定；默认全部 `DISABLED`，验收时显式启用。
- [x] 2.4 检查 SQL、映射、PO、Mapper 和 Repository 与冻结结构一致；不得先实现未确认字段。

## 3. Domain rules

- [x] 3.1 将数据源、MySQL 协议和 Tool 绑定的业务编排放在 Domain；Infrastructure 只负责持久化与适配。
- [x] 3.2 接入现有 JSqlParser 责任链，拒绝 malformed SQL、多语句、非 SELECT、写操作、DDL、DCL、事务、锁、文件输出、危险函数和超长 SQL。
- [x] 3.3 校验 SQL 命名占位符与扁平 request mapping 一一对应；保存前不执行 `EXPLAIN`、元数据查询或业务 SQL。
- [x] 3.4 统一生命周期为 `ENABLED` / `DISABLED`，移除 draft、published、deprecated 和固定模板初始化语义。
- [x] 3.5 合并数据源业务上限与 App Config 技术上限，按更严格值执行。

## 4. Infrastructure persistence

- [x] 4.1 创建通用数据源和 MySQL 协议的 PO、DAO、Mapper、Repository；模型类使用统一 Lombok 注解和 UTF-8 编码。
- [x] 4.2 改造 Tool/Mapping 查询返回完整协议类型、状态和 mapping，修复按 gateway 全量更新协议的条件。
- [x] 4.3 实现 `mcp_datasource` 凭证密文加解密适配器；明文密码、密钥和敏感 JDBC 参数不得进入日志、异常或 MCP 响应。
- [x] 4.4 将数据库唯一键、外键、连接和映射异常转换为 `ai-mcp-gateway-types` 统一异常；不新增 Infrastructure 异常包。
- [x] 4.5 删除 MySQL 固定模板 Factory、Registry 初始化和持久化缺失时的 Registry fallback。

## 5. Existing MCP chain integration

- [x] 5.1 改造 `tools/list`：只枚举当前 Gateway 的启用 Tool，按协议类型读取 HTTP/MySQL，禁止全局模板枚举。
- [x] 5.2 改造 `tools/call`：按 `(gateway_id, tool_name)` 保留协议类型并解析固定协议/数据源绑定，复用现有参数绑定和执行器。
- [x] 5.3 MySQL 分支只返回标准 MCP `name`、`description`、`inputSchema`，不泄露 SQL、JDBC URL、用户名、密文或内部 ID。
- [x] 5.4 保持 HTTP Tool 的发现、映射、参数转换和响应结构不变，并覆盖 MySQL/HTTP 并存场景。
- [x] 5.5 App Config 使用 `@Resource` / `@Bean` 装配控制库池、MySQL 运行时参数和密钥解析器，不使用手工 `final` 构造器注入外部依赖。

## 6. Real MySQL acceptance

- [x] 6.1 在清空后的控制库启用真实 `data_warehouse` 数据源、协议和 Tool，执行 `tools/list`。
- [x] 6.2 执行真实 `tools/call -> MySQL/JDBC -> data_warehouse`，验证 `fact_order` 与 `dim_channel` 的结构化只读结果。
- [x] 6.3 在本地开发验收中使用 root 账号验证参数绑定、行数/字节/列数/超时限制和 SQL 只读策略；客户端不能扩大执行范围。数据库账号权限不作为本变更的验收门槛。
- [x] 6.4 验证不存在表/列、无权限、数据源停用和网络失败在调用阶段安全返回。
- [x] 6.5 验证非法 SQL、注入、多语句、写操作、DDL、DCL、危险函数和参数错误在控制库写入前失败，目标库无查询。
- [x] 6.6 验证两态启停和协议变更通过新 `protocol_id` 重新绑定，不覆盖启用记录。
- [x] 6.7 验证日志、异常、审计、控制库和 MCP 响应无明文密码、密钥、Authorization 或完整敏感 URL。

## 7. Documentation and verification

- [x] 7.1 更新通用架构文档：数据源完整落库、连接池留在 App Config、业务编排在 Domain、数据库执行在 Infrastructure、外部依赖使用 IoC。
- [x] 7.2 保存 ER 图 SVG/PNG、字段矩阵、开发清库说明和真实验收记录。
- [x] 7.3 执行全量 Maven 测试、OpenSpec 校验和 `git diff --check`。
- [x] 7.4 仅在代码、测试、真实 MySQL 验收和文档全部完成后归档本变更；当前阶段不提交 Git。
