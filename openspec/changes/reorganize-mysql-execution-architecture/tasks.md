## 1. 架构基线与通用规范

- [x] 1.1 盘点现有 `ToolsCallHandler → ToolExecutorRouter → ToolExecutor` 链路、HTTP 策略和 MySQL 策略的依赖，固化重构前基线；验证方式：使用 `codegraph` 依赖结果和源码清单确认没有第二套路由链
- [x] 1.2 更新 `docs/architecture/README.md`，形成适用于所有后端的 Domain、Case、Infrastructure、App Config 职责和依赖规则；验证方式：文档审查并通过本地 Markdown 链接检查
- [x] 1.3 更新 `docs/architecture/design-patterns.md`，明确普通领域服务、策略、责任链和规则树的选择条件；验证方式：文档包含责任链结果语义、显式顺序和 Domain 规则归属
- [x] 1.4 增加架构依赖检查，确保 Domain 不依赖 JDBC、Hikari、JSqlParser、Environment 或 Infrastructure，且 Infrastructure 不反向依赖 App；验证方式：运行依赖扫描并确认无违规导入

## 2. App Config 启动配置归属

- [x] 2.1 将 `MysqlConnectionProperties`、`MysqlDataSourceConfig`、连接池参数、密钥引用和运行时开关归入 `ai-mcp-gateway-app/src/main/java/cn/bugstack/ai/config`；验证方式：配置类路径、package 和模块依赖符合 App Config 约束
- [x] 2.2 将 MySQL Bean 组装和 MVP 初始化迁移到 App Config，Infrastructure 不直接读取 `System.getenv`、`System.getProperty` 或 Spring Environment；验证方式：源码检索和应用上下文启动测试确认配置来源唯一
- [x] 2.3 建立 App Config 到 Infrastructure 的组合根适配入口，避免 Infrastructure 导入 App 配置类且不复制第二份配置模型；验证方式：编译通过并确认 `MysqlJdbcGateway` 可获得每个数据源的技术连接设置
- [x] 2.4 为未配置凭证、停用数据源和显式 MVP 开关增加配置测试；验证方式：未配置凭证时不注册目标数据源，停用时不获取 JDBC 连接

## 3. Domain 业务模型与查询流程

- [x] 3.1 将 Domain 中的启动配置字段替换为数据源引用、业务状态和查询策略模型；验证方式：Domain 不再出现 JDBC URL、运行时密码、Hikari 参数或环境变量语义
- [x] 3.2 将 `MysqlQueryResult` 列模型改为逻辑类型，保留既有 MCP 结构化结果兼容性；验证方式：结果测试覆盖 NULL、DECIMAL、日期时间、空结果和截断状态
- [x] 3.3 实现 Domain `MysqlTemplateQueryService`，完成模板状态、数据源状态、参数业务规则、策略合并、责任链调用和 `IMysqlQueryPort` 调用；验证方式：领域测试覆盖发布/停用模板、数据源状态、参数错误和策略收紧
- [x] 3.4 将模板和数据源状态迁移规则从 Registry 实现移入 Domain 服务或聚合；验证方式：状态迁移测试覆盖非法迁移和版本不可用场景

## 4. Domain SQL 安全责任链

- [x] 4.1 定义 `ISqlSafetyRule`、责任链上下文和 `CONTINUE`、`ALLOW`、`REJECT`、`FAIL` 结果语义；验证方式：领域测试断言四类结果不会混用
- [x] 4.2 将 SQL 安全规则放入 Domain 责任链，并按策略、解析事实、单语句、只读类型、副作用/锁、参数和资源限制显式排序；验证方式：责任链顺序测试和安全拒绝测试全部通过
- [x] 4.3 保留 `MysqlSqlParser` 在 Infrastructure，实现 Domain 的 SQL 分析 Port；验证方式：解析失败失败关闭，Domain 不依赖 JSqlParser API
- [x] 4.4 将参数必填/类型/声明校验留在 Domain，将命名参数转换和 PreparedStatement 绑定留在 Infrastructure；验证方式：非法参数不获取 JDBC 连接，绑定测试覆盖注释和字符串

## 5. 复用现有 Tool 转换链路

- [x] 5.1 保持 `ToolsCallHandler`、`ToolExecutorRouter`、`ToolExecutor` 契约和现有 HTTP 策略不变；验证方式：代码差异审查和 HTTP 回归测试确认没有新增路由或 Handler
- [x] 5.2 将 `MysqlTemplateExecutor` 保持为现有 Tool 链路中的 MySQL 策略适配器，委托 Domain 查询服务并转换为既有 `ToolExecutionResult`；验证方式：MySQL Tool 调用路径仍经过原 Router，未知后端错误语义不变
- [x] 5.3 增加 HTTP 与 MySQL 并存回归测试，证明新增 MySQL 策略不会改变 HTTP Tool 行为；验证方式：同一 Gateway 中两类 Tool 均可发现并调用
- [x] 5.4 删除或禁止重复的 Tool 路由、Handler、QueryAdapter 和 Parser 胶水类；验证方式：重复类检索和依赖图检查通过

## 6. Infrastructure 技术适配收敛

- [x] 6.1 缩减 `MysqlJdbcGateway`，只保留连接池、连接/Statement/ResultSet 生命周期、只读设置、超时、资源限制、技术结果映射和技术异常转换；验证方式：代码审查确认无模板状态、权限或领域 SQL 规则
- [x] 6.2 使模板和数据源 Registry 只承担读取/保存/删除；验证方式：Registry 测试确认状态机规则由 Domain 调用，适配器不直接决定业务状态
- [x] 6.3 将密钥解析、JDBC 类型转换和连接池指标放入 Infrastructure 技术包，并将技术异常类型统一放入 `ai-mcp-gateway-types` 的 `types.exception`；验证方式：Domain 无 `ISecretResolver`、JDBC 类型或连接池技术依赖，Infrastructure 无独立异常包
- [x] 6.4 保持一个内聚的 MySQL JDBC 适配器，不为连接、执行、结果映射和资源治理创建重复公开包装类；验证方式：适配器单元/集成测试和重复类检索通过

## 7. 验证与收尾

- [x] 7.1 为 Domain 查询服务、SQL 责任链、App Config 装配和层间依赖增加测试，测试统一放在 `ai-mcp-gateway-app/src/test/java/cn/bugstack/ai/test`；验证方式：测试路径和 package 声明符合架构规范
- [x] 7.2 运行 `git diff --check`、架构依赖检查和全量 `mvn test`；验证方式：命令全部成功且无编码或导入违规
- [x] 7.3 运行 `openspec validate reorganize-mysql-execution-architecture --json` 并核对实现与本变更任务；验证方式：OpenSpec 校验通过，未引入 DDL、DAO、PO 或 Mapper 变更
- [x] 7.4 输出重构验收记录，列出 App Config、Domain、Infrastructure 和现有 Tool 链路的最终文件归属；验证方式：评审记录与代码目录一致
