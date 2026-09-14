## 1. MVP 链路契约与测试环境

- [x] 1.1 固化 MVP 的 Tool、模板、输入参数、结构化结果和错误码样例，并逐项对照三份规格确认边界；验证方式：评审 `specs/**/*.md`，确认首个链路只包含 MySQL 只读模板查询。
- [x] 1.2 配置本机 `127.0.0.1:3306/data_warehouse` 作为 MVP 目标数据源，用户名和密码仅通过运行时环境变量或本地密钥注入；验证方式：连接测试成功执行 `SELECT 1`，且目标数据源不复用 Gateway 配置库连接、不在仓库中出现凭证。
- [x] 1.3 对 `data_warehouse` 执行只读元数据检查，确认可用 schema、表、列和可关联字段；验证方式：仅查询 `information_schema` 或等价只读元数据，形成候选查询表清单，不执行 CREATE、INSERT、UPDATE、DELETE、ALTER、DROP 或 TRUNCATE。
- [x] 1.4 根据已确认的真实表结构选择一个 JOIN/聚合或等价只读模板；验证方式：模板只引用实际存在的表和列，空结果也有明确断言；若没有适合的关联数据，使用独立测试实例补充，不修改 `data_warehouse`。
- [x] 1.5 验证候选 SQL Parser 对 MySQL 目标语法的支持矩阵，至少覆盖 SELECT、WITH SELECT、JOIN、聚合、参数占位符和拒绝语句；验证方式：形成 parser spike 测试报告，解析失败默认返回拒绝。

## 2. Domain 执行模型与后端路由

- [x] 2.1 定义后端类型、执行模式、Tool 执行上下文、统一结果和稳定错误码；验证方式：Domain 单元测试覆盖 HTTP、MySQL 模板、未知后端和缺失配置。
- [x] 2.2 定义 ToolExecutor 策略接口和按后端类型路由的 Domain Port；验证方式：路由测试证明 HTTP 与 MySQL 选择不同执行器，未知类型返回明确错误。
- [x] 2.3 将 Tool 执行职责从 `ISessionPort` 中拆出，保留 SessionPort 的会话同步职责；验证方式：编译通过，现有会话同步测试继续通过，Domain 不出现 JDBC、MyBatis 或连接池依赖。
- [x] 2.4 调整 `ToolsCallHandler` 所属用例，使其只负责 MCP 请求解析、用例调用和 MCP 响应，不直接判断 HTTP 或 JDBC；验证方式：Handler 单元测试通过，代码检索确认不存在后端技术分支。

## 3. HTTP 回归与通用 MCP 能力

- [x] 3.1 将现有 HTTP 调用适配到统一 ToolExecutor，并保持 GET/POST 兼容；验证方式：现有 `ToolsCallHandlerTest`、HTTP Gateway 测试和 Maven 测试全部通过。
- [x] 3.2 修正通用参数处理，确保多个顶层参数和嵌套参数不会只取第一个值；验证方式：增加包含两个以上独立参数的 POST/GET 回归测试并通过。
- [x] 3.3 使请求映射只使用 `mapping_type=request`，响应映射只参与结果转换；验证方式：混合 request/response 映射测试证明输入 Schema 不包含响应字段。
- [x] 3.4 统一 MCP 结果封装，将 `isError` 输出为布尔值并兼容原有文本结果；验证方式：HTTP 成功、HTTP 非 2xx 和异常场景的 JSON-RPC 响应断言通过。

## 4. MySQL 数据源与模板 Registry

- [x] 4.1 定义 MySQL 数据源配置和 Domain Repository/Port，仅允许 Tool 绑定固定数据源；验证方式：数据源解析测试证明客户端参数不能覆盖 datasourceRef。
- [x] 4.2 在测试 profile 实现配置型或内存型数据源 Registry，并将存取适配器放入 `infrastructure/adapter/repository`，不修改 Gateway 控制库 DDL；验证方式：启动测试上下文后可按 ID 获取数据源，停用数据源时调用被拒绝。
- [x] 4.3 定义只读模板 Registry、模板版本和发布状态，并将内存存取实现放入 `infrastructure/adapter/repository`；验证方式：草稿、停用、不存在和已发布模板的状态测试通过。
- [x] 4.4 使用已确认的 `data_warehouse` 实际表结构注册一个固定只读模板及输入 Schema；验证方式：`tools/list` 返回名称、描述、参数类型和必填项，不返回 SQL 正文或凭证，模板不假设未确认的业务表名。

## 5. MySQL JDBC 执行器

- [x] 5.1 实现 MySQL JDBC Gateway/Adapter 和独立连接池，支持连接超时、获取连接超时和健康检查；验证方式：测试数据源连接、断开和恢复测试通过，连接池指标可读取。
- [x] 5.2 实现模板参数校验和 PreparedStatement 参数绑定，禁止字符串拼接；验证方式：合法参数查询成功，缺失、类型错误、未声明和额外参数均返回 `SQL_PARAMETER_ERROR` 且不执行 SQL。
- [x] 5.3 实现只读连接、查询超时、最大行数、最大结果字节数和资源释放；验证方式：超时、异常、取消和结果超限测试确认 Statement、ResultSet、Connection 均被释放。
- [x] 5.4 实现列信息、行数据、NULL、DECIMAL、日期时间和截断状态到统一结果对象的映射；验证方式：JOIN、聚合、空结果和精度数据的结构化结果断言通过。

## 6. SQL 只读安全责任链

- [x] 6.1 定义 SQL 安全判断结果和拒绝错误语义；验证方式：Domain 单元测试覆盖允许、拒绝、解析失败和策略未配置四类结果。
- [x] 6.2 实现完整 SQL 解析、单语句校验、语句类型校验和副作用/锁定节点；验证方式：INSERT、UPDATE、DELETE、DDL、DCL、事务、多语句、FOR UPDATE、文件输出、CALL 和危险函数测试全部拒绝。
- [x] 6.3 实现参数占位符校验和资源策略节点；验证方式：未绑定参数、重复参数、超长 SQL、超大 maxRows 和超时请求均在 JDBC 执行前被拒绝。
- [x] 6.4 验证失败关闭行为和数据库只读权限；验证方式：Parser 解析失败不执行 SQL，测试账号无写权限，尝试写操作同时被应用层和数据库层阻断。

## 7. MCP 端到端纵向切片

- [x] 7.1 打通 `tools/list -> tools/call -> ToolExecutorRouter -> MysqlTemplateExecutor -> JDBC -> MCP` 测试链路；验证方式：使用本机 `data_warehouse` 中已确认的真实表调用只读模板 Tool 并返回结构化结果。
- [x] 7.2 增加 MCP 参数、Tool 权限、数据源状态和模板版本校验；验证方式：未授权、停用 Tool、数据源停用和未发布模板场景均不获取 JDBC 连接。
- [x] 7.3 增加查询超时、连接失败、SQL 拒绝、结果超限和 MySQL 执行异常的 MCP 错误映射；验证方式：每类场景返回稳定错误码、requestId/queryId 且不泄露敏感信息。
- [x] 7.4 增加 HTTP 与 MySQL 并存回归测试，确认一个 Gateway 可同时发现和调用两类 Tool；验证方式：端到端测试中 HTTP Tool 和 MySQL Tool 均成功，且彼此配置不串用。

## 8. 审计、指标与 MVP 验收

- [x] 8.1 记录 Tool 调用、SQL 策略决策、耗时、行数、结果字节数、数据源和错误码；验证方式：审计断言包含关联 ID，且敏感字段脱敏。
- [x] 8.2 增加 MySQL 查询成功、拒绝、超时、连接池和结果规模指标；验证方式：集成测试执行后指标计数和耗时记录可查询。
- [x] 8.3 执行模块级测试和全量 Maven 测试，修复 HTTP 回归问题；验证方式：`mvn test` 全部通过，新增 SQL 安全测试无失败。
- [x] 8.4 输出 MVP 链路验收记录，确认是否进入控制库持久化阶段；验证方式：验收记录明确列出已通过场景、未纳入能力和下一变更范围。

## 9. 控制面持久化前置确认

- [ ] 9.1 与 Xerina 确认数据源、模板、策略、Tool 绑定的业务含义、字段结构、密钥引用、兼容策略和迁移方案；验证方式：形成独立的表结构决策记录。
- [x] 9.2 在 9.1 完成前不新增或修改 Gateway 配置库 DDL、DAO、PO、Mapper；验证方式：代码评审和 `git diff` 确认本变更未引入未确认的表结构依赖。

## Deferred Beyond MVP

- 动态只读 SQL Tool 及其独立授权。
- 数据字典、表结构搜索和业务指标语义。
- 异步查询、游标分页、结果文件导出和查询取消。
- 字段级脱敏、行级业务权限和成本预估。
- MySQL 查询配置的正式管理 API、控制库表结构和迁移脚本。
