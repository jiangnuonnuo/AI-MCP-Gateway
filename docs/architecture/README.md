# 项目架构索引

## 文档定位

本文档是项目架构的轻量级入口。开发代码前先确认当前功能所属领域、调用路径和依赖方向，再按任务渐进阅读关联章节与专项文档。

详细的 DDD、六边形架构以及 Domain、Case、Infrastructure 的设计准则遵循 `$xfg-ddd-skills` 技能。该技能是本项目进行架构设计和实现时的开发准则；本项目文档只记录项目实际采用的约束，不重复完整技能内容。

专项设计模式规范见 [设计模式规范](design-patterns.md)。部署和节点关系见 [分布式架构图](../ai-mcp-gateway-distributed-architecture.drawio)。

## 总体结构

项目采用 DDD 与六边形架构：领域层承载业务规则，外部入口和基础设施通过接口与领域交互，依赖方向指向领域核心。

```text
Trigger ──> API ──> Case ──> Domain <── Infrastructure
```

项目模块职责如下：

| 模块 | 职责 |
| --- | --- |
| `ai-mcp-gateway-api` | 对外接口、请求响应 DTO、公共响应结构 |
| `ai-mcp-gateway-trigger` | HTTP 等外部入口和请求适配 |
| `ai-mcp-gateway-case` | 跨领域用例编排、复杂流程协调 |
| `ai-mcp-gateway-domain` | 领域模型、领域服务、Repository/Port 接口和业务规则 |
| `ai-mcp-gateway-infrastructure` | Repository 与 Port 实现、DAO/PO、Redis、远程 Gateway 和技术资源适配 |
| `ai-mcp-gateway-types` | 跨模块共享的基础类型 |
| `ai-mcp-gateway-app` | 应用启动、模块装配和运行配置 |

## 分层边界

### Domain

- 按领域组织模型、实体、值对象、领域服务和聚合边界。
- 定义 `adapter/repository` 与 `adapter/port` 接口，表达数据访问和外部能力需求。
- 只依赖领域抽象，不直接依赖 DAO、Redis、HTTP 客户端或其他基础设施实现。
- 业务流程、业务判断、状态转换、领域约束、策略选择、领域内循环和责任链留在 Domain。
- Domain 只表达稳定的业务语义，不承载 JDBC、连接池、环境变量、HTTP 客户端或框架技术细节。

### Case

- 面向用例组织跨领域流程，负责领域服务之间的协调、事务边界和结果组合。
- 不直接访问 DAO、Redis 或远程 Gateway。
- 单一领域、步骤简单且没有跨领域协调时，可以由 Trigger 直接调用 Domain，跳过 Case。
- 涉及多个领域、多个聚合或需要统一编排的流程，必须通过 Case 组织调用。
- Case 不重复建设领域规则，也不因新增一种后端执行方式而重写已有 Tool 转换和路由链路。

### Infrastructure

- 在 `adapter/repository` 实现 Domain 的 Repository，在 `adapter/port` 实现 Domain 的 Port。
- Repository 负责调用 `dao`、`redis` 或内存存储并完成聚合数据映射；有状态的配置 Registry 属于仓储适配器，即使 Domain 接口暂时保留 Registry 命名，也不得放入 `adapter/port`。Redis 会话/发布订阅等外部能力仍按 Port 归类。
- Port 负责调用 `gateway`、远程客户端或其他外部能力并完成 DTO 转换；实现 Domain Port 的执行器、解析器和会话外部能力才进入 `adapter/port`。
- 不实现 Domain Port 的参数绑定器、指标收集器和连接池辅助组件，必须按技术职责放在 `mysql`、`observability` 等基础设施包，不能借用 `adapter/port` 目录；异常类型统一放在 `types.exception`。
- Registry 适配器只提供领域要求的查询、保存和删除，不在适配器内实现发布、停用或其他状态机。
- Infrastructure 只负责外部技术访问、连接池、解析、绑定、结果映射、资源生命周期和技术异常转换。
- Infrastructure 不承载领域规则、业务状态机、策略合并、权限判断或业务责任链，不绕过 adapter 与 Domain 通信。

#### Repository 与 Port 判定

以职责而不是类名决定目录：

| 实现内容 | 目录 | 判断依据 |
| --- | --- | --- |
| 控制库 DAO/PO 映射、内存模板或数据源 Registry | `adapter/repository` | 面向可持久化集合，负责查询、保存、删除 |
| JDBC 查询、HTTP 调用、Redis 会话、SQL Parser、ToolExecutor | `adapter/port` | 实现 Domain 对外部能力的 Port 或既有 Tool 策略 |
| Tool 授权、执行审计等外部策略状态 | `adapter/port` | 通过 Domain Port 提供外部策略/观测能力，不是聚合仓储 |
| 参数绑定、结果计量、连接池内部协调、驱动异常 | `infrastructure/<technical-package>` | 只是适配器内部技术协作，不是 Domain Port |

`adapter/port` 文件较多并不代表要为每个步骤创建 Port。只有存在稳定的 Domain 接口、可替换外部系统、独立资源边界或独立生命周期时才保留一个端口实现；同一 JDBC 适配器内部的连接、绑定、结果映射和资源释放应保持内聚。

### App Config

- `ai-mcp-gateway-app/src/main/java/cn/bugstack/ai/config` 是启动配置和组合根。
- 连接地址、连接用户名、密钥引用、连接池参数、超时、并发、运行时开关和 `@ConfigurationProperties` 均归入 App Config。
- App Config 负责通过 `@Bean`、`@Resource` 和配置属性完成 Bean 组装，将配置适配给 Infrastructure；Infrastructure 不得反向依赖 App。
- App Config 不承载领域流程和业务规则；配置对象是启动参数，不得被当作 Domain 聚合或业务实体。

### 通用业务边界

所有后端能力均遵循以下判定，而不是按文件名或技术目录判定职责：

| 问题 | 所属层 | 约束 |
| --- | --- | --- |
| 是否涉及业务不变量、业务状态、业务决策或业务流程？ | Domain | 由领域模型、领域服务、策略或责任链实现 |
| 是否涉及多个领域、协议转换、事务边界或用例结果组合？ | Case | 只协调 Domain，不复制领域规则 |
| 是否涉及数据库、网络客户端、连接池、序列化、解析器或资源释放？ | Infrastructure | 只实现 Port，不决定业务结果 |
| 是否涉及启动参数、环境变量、Bean 装配或运行时开关？ | App Config | 只负责配置和组合根 |

禁止以“数据库执行需要校验”为理由把业务校验放入 Infrastructure；也禁止以“流程需要循环”为理由把业务流程下沉到技术适配器。业务循环和控制流属于 Domain，技术资源循环（例如遍历 ResultSet）才属于 Infrastructure。

### 依赖方向

```text
Trigger/API → Case → Domain ← Infrastructure
                         ↑
                       App Config 负责组合和注入
```

- Domain 可以定义 Port，但不能引用 Infrastructure 实现或 App Config 类。
- Infrastructure 可以实现 Domain Port，但不能反向引用 App 模块。
- App 可以引用 Case、Domain 和 Infrastructure，并在组合根注入配置与适配器。
- 任何跨层技术对象都必须在边界处转换，禁止 JDBC、HTTP DTO、Spring Response 或环境变量语义穿透 Domain。

## 工程实现规范

### 异常归属

- 跨模块稳定的业务错误码和对外错误语义统一放在 `types.exception`，优先复用 `AppException`，需要保留输入校验语义时可使用 `IllegalArgumentException` 的专用子类，例如 `MysqlDomainException`、`MysqlQueryException` 和 `MysqlParameterException`；Domain 服务包不得作为公共异常类型出口。
- Infrastructure 只负责在技术边界捕获 JDBC、HTTP、Redis 等原始异常并转换错误码；不得在 Infrastructure 模块建立独立异常类型或 `exception` 包，也不得把异常放到 `adapter/port` 伪装成端口。
- Domain 抛出稳定业务异常时只依赖 `types.exception`，不得引用 Infrastructure 异常；技术异常与业务异常通过错误码和专用类型区分，而不是通过模块位置区分。

#### 异常传播与错误码规范

```text
外部驱动/客户端异常
        ↓ Infrastructure 边界捕获并转换错误码
types.exception 统一异常类型
        ↓ Tool/Trigger 边界映射
稳定业务错误码与安全响应
```

- `types.exception` 是所有模块统一的异常契约层，只允许放置稳定错误码、基础异常和具有明确公共语义的专用异常；不得引用 Domain、Case、Infrastructure 或 Spring 类型。
- Domain 只抛出业务语义异常，例如模板状态、数据源状态、参数规则和安全策略拒绝；不要把 SQLState、JDBC 类型、连接池名称或驱动消息带入 Domain 异常。
- Infrastructure 在调用 JDBC、HTTP、Redis 等外部系统的边界捕获原始异常，创建 `types.exception` 下的技术专用异常并保留 cause 供内部诊断；技术异常消息必须脱敏，不得包含密码、Token、Authorization、JDBC URL 或完整 SQL。
- Tool、Trigger 或其他对外适配器负责把业务异常和技术异常映射为稳定错误码及 `isError` 响应；对外只返回错误码和非敏感提示，不返回堆栈或底层驱动文本。
- 同一业务语义只保留一个稳定错误码。禁止在每个方法、每个驱动异常分支中创建重复异常类；当错误码足以表达语义时，复用 `AppException`，只有调用方需要区分类型或保留基础异常语义时才创建专用子类。
- 责任链中的“业务拒绝”“解析失败”“技术异常”必须使用不同状态和错误码；不得用 `null`、空字符串或通用 `RuntimeException` 隐式表示失败。
- 测试必须断言异常类型或稳定错误码，并验证响应和日志不泄露敏感信息；不得依赖 JDBC、HTTP 客户端的原始异常文本。

### 测试目录

- 所有测试源文件统一放在 `ai-mcp-gateway-app/src/test/java/cn/bugstack/ai/test` 下，按 `domain`、`infrastructure`、`trigger` 等职责继续分包。
- 所有测试资源统一放在 `ai-mcp-gateway-app/src/test/resources` 下；Domain、Infrastructure 等业务模块不得新增独立 `src/test` 测试目录。
- 新增或迁移测试时必须同步修正 `package` 声明和跨模块导入，确保测试通过应用模块依赖运行，不通过测试文件位置绕过模块边界。

### 领域模型

- Domain 的实体、值对象、Command、VO 及其嵌套数据对象优先采用项目统一的 Lombok 形式：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`；只有存在明确生命周期或框架约束时才保留手写构造器，并说明原因。
- 普通领域模型不得默认使用 `record`、`final class` 或 `final` 字段表达不可变性；只有确有不可变、安全或并发语义时才允许使用，并在类注释中说明原因。
- 每个领域模型必须有类级头注释；每个对象字段必须在字段声明前使用注释说明业务含义、单位、状态、是否必填或敏感性，禁止只依赖字段名猜测语义。
- 模型注释不得描述“已修改”“新增”“已修复”“新版本”等过程信息，只描述稳定的业务约束和设计意图。

### 基础设施依赖注入与拆分

- Spring 管理的 Domain、Case 和 Infrastructure 组件统一使用项目现有的 `@Component`、`@Service` 和 `@Resource` 进行 IoC 注入；依赖字段不得默认使用 `private final`，不得为 Spring Bean 编写多级手工构造器。
- 纯工具类、解析器和无状态策略可以使用无参构造和内部默认实现；需要替换实现时通过 Spring Bean 或测试工具注入，不在生产代码中保留仅为组装依赖的重载构造器。
- 外部对象包括 Domain Port 实现、Repository、DAO、Gateway、Mapper、远程客户端、配置对象、策略集合和基础设施服务；这些对象必须由 IoC 容器通过 `@Resource`、`@Autowired` 字段或 `@Bean` 方法参数引入，禁止在业务组件中 `new` 出替代实例。
- 外部依赖字段不得使用默认对象、默认空集合或默认 Lambda 作为兜底值，例如 `@Resource private List<ToolExecutor> executors = List.of();` 属于错误写法；容器未注入时应让装配失败，确需可选依赖时必须显式声明可选语义并在调用处处理。
- `@Bean` 工厂方法中允许使用无参 `new` 创建被容器管理的对象，例如 `return new MysqlJdbcGateway();`；但不得通过构造器手工传入外部依赖，外部依赖仍须由容器注入。
- 测试代码可以通过无参构造、Mock 或测试工具注入替身；测试用的手工组装不得反向成为生产组件的构造器设计。
- 只有存在独立领域端口、独立生命周期、独立资源边界或独立测试价值时才拆分 Infrastructure 类；单纯转发调用的胶水类应合并到所属适配器，避免一个后端形成过多同层文件。
- 一个适配器可以承载同一外部系统的连接、执行、结果映射和资源治理；当职责仍属于同一技术边界时，优先保持内聚，不为每个私有步骤创建公开类。

#### 禁止复现的反例

以下写法均不得出现在 Spring 管理的业务组件中：

```java
// 反例：使用 final 依赖字段和手工构造器注入外部对象
private final IMysqlDataSourceRegistry registry;
private final ISqlSafetyPort safetyPort;

public MysqlJdbcGateway(IMysqlDataSourceRegistry registry, ISqlSafetyPort safetyPort) {
    this.registry = registry;
    this.safetyPort = safetyPort;
}
```

正确形式是：

```java
@Resource(name = "mysqlDataSourceRegistry")
private IMysqlDataSourceRegistry registry;

@Resource
private ISqlSafetyPort safetyPort;
```

以下写法也属于反例：

```java
// 反例：默认实例会掩盖容器未完成装配
@Resource
private List<ToolExecutor> executors = List.of();
```

应改为：

```java
@Resource
private List<ToolExecutor> executors;
```

集合、Parser、参数绑定器等只有在确认是组件外部依赖时才使用 `@Resource`；纯内部无状态工具可以直接使用无参构造，但不能把外部 Bean 伪装成内部默认对象。

#### 基础设施拆分反例

- 适配器已经实现 Domain Port 时，不得再增加只做一层转发的公开 Adapter；例如 `MysqlJdbcGateway` 已实现 `IMysqlQueryPort` 时，不再增加同样只调用它的 `MysqlQueryAdapter`。
- 同一 SQL 安全组件内部的词法扫描和 AST 解析，如果没有独立端口、生命周期或对外契约，应保持在同一 Parser 适配器中，不得为每个解析步骤创建公开类。
- Registry、执行器、安全策略、连接池和指标只有在具有独立端口、状态或资源边界时才拆分；否则应收敛到所属适配器。

### 编码与验证

- Java、Markdown、YAML 和配置文件统一使用 UTF-8 编码；出现中文注释或文档时，修改后必须检查文件编码和乱码。
- 提交前至少执行 `git diff --check`、全量 `mvn test`，并对新增或迁移的集成测试执行对应的运行时验证。
- 测试目录、模型注解、字段注释和编码检查未通过时，不得提交实现结果。

## 领域拆分与增量开发

当前代码按业务能力划分为 `gateway`、`protocol`、`auth`、`session`、`admin` 和 `llm` 等领域。新增需求先判断它是否属于已有领域的业务语言和生命周期：

- 与已有领域共享核心概念、状态和不变量，只是增加该领域的一个能力或流程时，在已有领域内增量开发。
- 需求拥有独立的业务目标、核心概念、规则和生命周期，或与已有领域存在清晰的边界上下文时，拆分为新的领域模型。
- 只有技术目录不同、复用同一组业务规则，不能作为拆分新领域的理由。
- 跨领域需求不通过一个领域持有另一个领域的 Repository 解决；由 Case 编排，或通过明确的 Port、事件和领域契约协作。
- 新领域或大范围跨领域改动先明确边界、输入输出和依赖方向，再按可验证的小功能逐步落地。

## 调用路径选择

```text
单领域简单功能：Trigger → Domain → Repository / Port

跨领域或复杂用例：Trigger → Case → Domain → Repository / Port
```

Case 不是所有请求的必经层。是否使用 Case 由业务边界和编排复杂度决定，而不是由接口数量或代码行数决定。

## 开发前检查

1. 确认需求属于已有领域增量，还是独立领域模型。
2. 确认调用属于简单单领域路径，还是需要 Case 编排的复杂路径。
3. 确认 Domain 只依赖抽象，Infrastructure 通过 adapter 实现抽象。
4. 确认多实现、串联规则和条件分支分别采用合适的设计模式；具体选择见 [设计模式规范](design-patterns.md)。
5. 按 `$xfg-ddd-skills` 的相关参考文档检查模型、聚合、Repository、Port 和层间依赖。
