## Context

现有 `ToolsCallHandler`、`ToolExecutorRouter` 和 `ToolExecutor` 已经形成稳定的通用 Tool 执行链。HTTP Tool 通过实现 `ToolExecutor` 接入该链路；MySQL 也应当作为同一链路中的新增执行策略，而不是重新建立一套 Tool 路由或 MCP 调用流程。

当前 MySQL 实现的主要问题是职责混合：`MysqlTemplateExecutor` 和 `MysqlSqlSafetyChain` 位于 Infrastructure，却包含领域流程和治理规则；`MysqlJdbcGateway` 同时处理模板状态、参数业务校验、策略合并和 JDBC 执行；`MysqlDataSourceConfig` 同时被当作领域模型和启动配置；连接池和密钥读取仍散落在 Infrastructure 配置中。

项目的应用模块已经存在 `ai-mcp-gateway-app/src/main/java/cn/bugstack/ai/config`，并且应用模块依赖 Infrastructure。该依赖方向允许应用配置在组合根通过 `@Bean` 参数把启动配置适配给 Infrastructure，但 Infrastructure 不得反向依赖 App 模块。

## Goals / Non-Goals

**Goals:**

- 保持现有 Tool 转换、Tool 路由、MCP Handler 和 HTTP 回归链路不变。
- 让 MySQL 业务流程、状态判断、参数业务规则、策略合并和 SQL 安全责任链位于 Domain。
- 让 `MysqlConnectionProperties`、`MysqlDataSourceConfig` 和连接池启动属性位于 App Config。
- 让 Infrastructure 只负责 MySQL 技术执行、Parser、参数绑定、结果映射和资源生命周期。
- 将通用分层与设计模式规则写入架构文档，并可约束未来的 HTTP、Redis 或其他后端实现。
- 以最少的新增类完成边界重整，避免为现有 Tool 链路增加重复 Router、Handler 或 Adapter。

**Non-Goals:**

- 不重新设计 `ToolsCallHandler → ToolExecutorRouter → ToolExecutor` 链路。
- 不新增动态 SQL、写操作、异步查询、字段脱敏或新的 MCP 协议能力。
- 不在未确认表结构前新增或修改 DDL、DAO、PO、Mapper 或控制面持久化。
- 不把 JDBC、Hikari、JSqlParser、ResultSet 或环境变量 API 引入 Domain。
- 不为了“分层完整”拆出只做转发的公开类。

## Decisions

### 1. 复用现有 Tool 链路，只增加 MySQL 策略

保持以下调用路径和接口不变：

```text
MCP Handler
    → ToolExecutorRouter
        → HttpToolExecutor
        → MysqlTemplateExecutor
```

不新增 `ToolCallUseCase`、第二套路由器、第二套 MCP Handler 或后端专用响应链。`MysqlTemplateExecutor` 作为现有 `ToolExecutor` 的一种实现接入容器，负责上下文转换、调用 Domain 查询服务和将领域结果转换为既有 `ToolExecutionResult`。

`ToolExecutorRouter` 继续负责后端类型/执行模式策略选择；这属于已有 Tool 业务链路，不在本变更中迁移或重写。

### 2. Domain 承载 MySQL 业务流程和规则

新增或重构 `MysqlTemplateQueryService` 作为 Domain 服务，负责：

1. 获取已发布模板并判断模板生命周期状态；
2. 校验固定数据源引用及业务可用状态；
3. 校验必填、类型、重复、未声明和多余参数；
4. 合并模板、数据源和调用方策略，只允许收紧上限；
5. 调用 SQL 安全责任链；
6. 生成领域 `MysqlQueryCommand` 并调用 `IMysqlQueryPort`；
7. 返回领域结果和稳定领域错误。

`MysqlTemplateExecutor` 不直接实现上述业务判断，也不访问 JDBC；它只适配现有 Tool 契约。

### 3. SQL 安全在 Domain 使用责任链

Domain 定义责任链上下文、规则接口和安全决策。规则按显式顺序执行：

```text
PolicyConfiguredRule
  → SqlAnalysisRule
  → SingleStatementRule
  → ReadOnlyStatementRule
  → SideEffectAndLockRule
  → TemplateParameterRule
  → ResourcePolicyRule
```

每个节点只处理一个业务规则，结果明确区分继续、允许、拒绝和失败。链路通过 Factory 或显式顺序组装，不能依赖 Spring Bean 的不确定扫描顺序。

Infrastructure 的 `MysqlSqlParser` 只提供不含 JSqlParser 类型的 SQL 分析事实，实现 Domain 的解析 Port。解析失败、无法判断和不支持语法统一由 Domain 责任链失败关闭。

### 4. 启动配置全部归 App Config

以下对象统一位于：

```text
ai-mcp-gateway-app/src/main/java/cn/bugstack/ai/config
```

- `MysqlConnectionProperties`；
- `MysqlDataSourceConfig`；
- JDBC URL、用户名、密钥引用、连接池、超时、并发和结果限制；
- `@ConfigurationProperties`、MVP 开关、`@Bean` 组装和运行时注册。

这些对象表示启动配置，不是 Domain 聚合或值对象。Domain 只保留 `MysqlDataSourceRef`、状态、业务策略等业务语义模型。

由于依赖方向是 `app → infrastructure → domain`，Infrastructure 不得导入 App 配置类。App Config 在组合根通过 `@Bean` 方法参数和适配器配置入口，把启动配置转换为 Infrastructure 可消费的技术设置；不得通过 Infrastructure 反向依赖 App，也不得把同一份配置模型复制到 Infrastructure。

应用配置中的外部 Bean 使用 `@Resource` 或 `@Bean` 方法参数注入。禁止在业务组件中使用 `private final` 手工构造器注入，禁止用默认空集合或默认实例掩盖 IoC 装配失败。

### 5. Infrastructure 只保留技术执行

`MysqlJdbcGateway` 只负责：

- 使用 App Config 传入的连接属性创建和维护 Hikari 连接池；
- 获取连接、设置只读、超时、最大行数和资源限制；
- 调用参数绑定器创建 PreparedStatement；
- 执行查询、读取 ResultSet 和释放连接/Statement/ResultSet；
- 将 JDBC 元数据映射为 Domain 逻辑结果；
- 将驱动、连接、超时和资源异常转换为技术错误。

它不得判断模板发布状态、数据源业务状态、调用权限、SQL 是否符合领域安全规则或策略如何合并。

`MysqlTemplateParameterBinder` 只处理命名参数转换、绑定顺序和 PreparedStatement 值传递。参数是否必填、类型是否正确、是否声明等由 Domain 判断。

### 6. Registry 只负责存取

模板和数据源 Registry 适配器只负责查询、保存和删除。发布、停用、废弃、版本有效性和状态迁移由 Domain 服务或聚合完成。

由于 Registry 持有模板/数据源集合状态，MVP 内存实现归入
`infrastructure/adapter/repository`；它们不是 JDBC、HTTP 或解析等外部能力 Port。
`adapter/port` 只保留实现 Domain 外部能力 Port 或既有 `ToolExecutor` 的适配器。
参数绑定器和指标分别归入 `infrastructure/mysql`、`infrastructure/observability`；
技术异常统一归入 `ai-mcp-gateway-types` 的 `types.exception`，避免各基础设施模块
重复定义异常类型或按文件名堆叠操作类。

MVP 内存 Registry 的装配开关和初始数据由 App Config 控制，不能因为环境变量存在就自动改变生产行为。

### 7. 通用架构规则和设计模式

架构文档必须面向所有后端能力描述：

- Domain：业务模型、不变量、业务流程、规则、状态转换、策略、责任链和领域内控制流；
- Case：只有在现有系统确实存在跨领域用例协调、事务边界或协议组合时才使用；本次不重写现有 Tool 链路；
- Infrastructure：外部技术访问和资源生命周期；
- App Config：启动属性、运行时配置和 Bean 组合根；
- 策略模式用于选择不同执行方式；责任链用于有序规则；规则树用于业务条件路由；普通流程不强行套模式。

## Risks / Trade-offs

- [配置模型移动导致模块引用变化] → 由 App Config 作为组合根完成适配，Infrastructure 不反向依赖 App，并增加应用上下文测试。
- [MySQL 策略接入破坏现有 Tool 链路] → 不修改 Router、Handler 和 ToolExecutor 契约，增加 HTTP/MySQL 并存回归测试。
- [责任链拆分过细] → 只拆出独立变化或独立测试的规则节点，简单规则保留在领域服务。
- [Domain 结果模型变化导致 MCP 字段不兼容] → Infrastructure 做逻辑类型映射，保留结构化结果兼容测试。
- [MVP 配置误连生产数仓] → App Config 使用显式 profile/property 开关，未配置凭证时不注册目标数据源。
- [技术适配器重复实现业务规则] → 通过代码审查和架构依赖扫描禁止 Infrastructure 出现模板状态、权限和 SQL 治理判断。

## Migration Plan

1. 固化现有 Tool 链路和 MySQL 行为基线，确认 HTTP 与 MySQL 测试均通过。
2. 将 `MysqlConnectionProperties`、`MysqlDataSourceConfig` 和连接池启动属性迁移到 App Config，建立应用组合根到 Infrastructure 的配置适配入口。
3. 在 Domain 中引入数据源业务引用模型、逻辑结果模型、查询服务和 SQL 安全责任链。
4. 将 `MysqlTemplateExecutor` 调整为现有 `ToolExecutor` 的 MySQL 策略适配器，委托 Domain 查询服务，不改 Router/Handler。
5. 缩减 `MysqlJdbcGateway` 和 `MysqlTemplateParameterBinder`，删除其中的领域状态、策略合并和参数业务判断。
6. 将 Parser 保持为 Infrastructure 技术适配，实现 Domain 的 SQL 分析 Port；Registry 只保留存取职责。
7. 更新通用架构文档、责任链规范、配置归属规范和 IoC 规范。
8. 执行架构依赖检查、责任链顺序测试、App Config 上下文测试、HTTP 回归、MySQL 纵向测试和全量 Maven 测试。
9. 不触碰控制库 DDL、DAO、PO、Mapper；持久化另立变更。

回滚时按阶段恢复 Bean 别名和旧适配器入口即可；不改变数据库结构和对外协议。
