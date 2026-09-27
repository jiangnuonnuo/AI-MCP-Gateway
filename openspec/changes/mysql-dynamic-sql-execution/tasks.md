## 1. 控制面契约与迁移门槛

- [x] 1.1 输出 `mcp_protocol_mysql` 执行模式字段、`sql_text` 可空性、默认值、检查约束、索引、旧数据回填和回滚方案的 ER/字段矩阵，并由 Xerina 确认业务含义、兼容性和迁移方案后再继续；以设计证据文件存在且字段矩阵与现有 PO/Mapper 一致为验收。
- [x] 1.2 在确认后的迁移脚本中为旧协议回填 `TEMPLATE`，为动态协议允许 `DYNAMIC_READONLY`，验证迁移前后旧模板记录、数据源外键和 Tool 逻辑关联不变，并验证回滚脚本可恢复旧结构。
- [x] 1.3 扩展控制面 PO、Mapper、Repository 和运行时协议 VO，验证模板记录默认解析为 `TEMPLATE`、动态记录解析为 `DYNAMIC_READONLY`，未知模式失败关闭；执行控制面 Repository 契约测试。

## 2. 领域模式与 SQL 治理责任链

- [x] 2.1 增加 `TEMPLATE`/`DYNAMIC_READONLY` 执行模式和值对象，验证 Tool 路由能明确区分两种模式且不再通过 `tool_type` 推断执行模式。
- [x] 2.2 将模板解析结果和动态请求解析结果收敛为统一 MySQL 查询命令，验证命令始终携带服务端数据源引用、有效只读策略、SQL 文本、绑定参数和 queryId，客户端无法覆盖数据源或策略。
- [x] 2.3 调整 SQL 治理责任链，保留显式规则顺序：请求形状、长度、完整解析、单语句、只读类型、副作用、模式参数规则和资源策略；验证任一失败规则短路且不进入 JDBC Port。
- [x] 2.4 为动态模式实现首版参数规则：只接受 `sql` 字符串和 `parameters` 对象、仅允许命名参数和 JSON 标量值，拒绝位置参数、重复参数、列表展开、标识符替换和 SQL 片段；执行领域失败测试并断言稳定错误码。

## 3. MySQL Infrastructure 执行链路

- [x] 3.1 将现有模板参数绑定能力扩展为可绑定外部 SQL 的共享绑定器，验证字符串、注释和标识符中的伪占位符不会被替换，参数值不会拼接回 SQL。
- [x] 3.2 新增动态 MySQL Tool 执行策略并复用现有 `IMysqlQueryPort`、连接池、结果限制和异常映射；验证动态模式不构造临时模板、不访问 HTTP、不创建第二套 JDBC 资源链路。
- [x] 3.3 验证成功、解析拒绝、策略拒绝、参数错误、数据源不可用、超时、并发上限和结果限制路径均释放 Connection、PreparedStatement、ResultSet，并返回现有 `MysqlQueryResult`/稳定 Tool 错误语义。

## 4. MCP Tool Schema 与正式调用

- [x] 4.1 为动态模式生成 `tools/list` Schema：顶层必填 `sql` 和 `parameters`，参数对象允许动态命名键，顶层拒绝数据源、JDBC URL、凭证和策略覆盖字段；执行 Schema 契约测试。
- [x] 4.2 调整 Tool 参数验证和路由，使动态请求不经过模板 mapping 的参数白名单，同时仍在连接前校验 Tool 状态、权限、请求大小和参数形状；执行缺失 SQL、非对象参数、未知后端字段、停用 Tool 和无权限测试。
- [x] 4.3 通过正式 MCP `tools/call` 接入动态执行器，验证 SSE 与 Streamable 两种协议均能传入 SQL/参数并返回真实结构化结果，且模板 Tool/HTTP Tool 的请求和响应保持兼容。

## 5. 分层测试与回归

- [x] 5.1 增加 Domain 责任链测试，覆盖合法 SELECT、合法 WITH/JOIN/聚合、写操作、DDL、锁、文件输出、危险函数、多语句、解析失败、参数缺失、多余和类型错误，并断言拒绝时未调用查询端口。
- [x] 5.2 增加 Infrastructure 绑定和 JDBC 生命周期测试，验证真实 PreparedStatement 参数、最大行/列/字节、超时、并发和资源释放；测试资源统一放在 `ai-mcp-gateway-app/src/test`。
- [x] 5.3 增加 Tool/MCP 契约测试，验证动态 `tools/list` Schema、`tools/call` 路由、固定数据源、错误映射、queryId 和模板/HTTP 回归。

## 6. 真实 MySQL 与 MCP 验收

- [x] 6.1 新建一个专用验收 Gateway，创建并启用动态只读 MySQL Tool，绑定动态协议记录、真实只读数据源和最小测试数据；验证 Gateway、Tool、协议、数据源四者的持久化关联和启用状态来自当前 Gateway，不使用全局协议枚举或 Mock 成功响应。
- [x] 6.2 通过该新 Gateway 的真实 MCP SSE `tools/list`，验证返回的仅是当前 Gateway 已启用的动态 Tool，Schema 必须包含 `sql` 与 `parameters`，且不包含数据源、凭证或策略字段。
- [x] 6.3 通过同一新 Gateway 的真实 MCP SSE `tools/call` 执行一条带命名参数的动态 SELECT，保存脱敏请求、响应 JSON、queryId、列/行/截断结果和数据库事实，验证结果来自绑定的目标 MySQL。
- [x] 6.4 通过同一新 Gateway 的真实 MCP Streamable 执行同一动态查询并对比 SSE 结果，验证两种传输的 Tool 名称、参数绑定、结构化结果和错误语义一致。
- [x] 6.5 在该新 Gateway 上执行动态 SQL 拒绝矩阵：写操作、DDL、锁、文件输出、多语句、语法错误、缺失/多余/非法参数、客户端数据源覆盖、停用 Tool、停用数据源、超时和结果限制；验证所有拒绝在 JDBC 连接前或资源安全路径结束。
- [x] 6.6 保存 `evidence/real-dynamic-sql-acceptance.md`，记录新 Gateway 标识、Tool/协议/数据源绑定摘要、MCP `tools/list`/`tools/call` 请求响应、真实数据库结果、失败用例和资源释放事实；证据不得包含密码、Token、JDBC URL 或完整敏感结果。

## 7. 构建与变更验证

- [x] 7.1 执行 `mvn test`、`git diff --check` 和 `openspec validate --change mysql-dynamic-sql-execution --strict`，验证新增动态能力、既有模板/HTTP Tool 和 MCP 传输回归均通过。
- [x] 7.2 复核本变更未引入日志格式、审计持久化、Text-to-SQL 生成、schema 元数据或查询计划实现；将后续日志/审计任务登记为独立变更输入，不阻塞本次动态执行验收。

## 8. 动态 Tool 控制面与可视化验收

- [x] 8.1 增加独立的动态绑定管理命令、DTO、Case、Repository 和 `save_mysql_dynamic_binding` 管理接口；动态绑定只要求 Gateway、固定数据源和资源护栏，不要求 `protocolId` 或 SQL 模板。
- [x] 8.2 将 Tool 绑定页面改为执行模式选择：`TEMPLATE` 继续选择已发布模板，`DYNAMIC_READONLY` 选择固定数据源并配置行数、字节数、列数和超时上限；列表、详情和状态校验展示动态模式。
- [x] 8.3 动态绑定保存为禁用状态，启用时同时校验数据源并同步动态协议状态；删除最后一个动态绑定时清理其动态协议记录，避免产生不可见协议。
- [x] 8.4 将真实验收从测试临时 SQL 写库改为调用 Gateway 管理接口和动态绑定管理接口创建资源，再通过 SSE/Streamable 的真实 MCP `tools/list`/`tools/call` 验证闭环；保存可复现的产品级验收入口和结果。
