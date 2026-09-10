# 项目架构索引

## 文档定位

本文档是项目架构的轻量级入口。开发代码前先确认当前功能所属领域、调用路径和依赖方向，再按任务渐进阅读关联章节与专项文档。

详细的 DDD、六边形架构以及 Domain、Case、Infrastructure 的设计准则遵循 [$xfg-ddd-skills](/Users/jiang/.codex/skills/xfg-ddd-skills/SKILL.md)。该技能是本项目进行架构设计和实现时的开发准则；本项目文档只记录项目实际采用的约束，不重复完整技能内容。

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
| `ai-mcp-gateway-infrastructure` | Repository 与 Port 实现、DAO/PO、Redis、远程 Gateway 和配置 |
| `ai-mcp-gateway-types` | 跨模块共享的基础类型 |
| `ai-mcp-gateway-app` | 应用启动、模块装配和运行配置 |

## 分层边界

### Domain

- 按领域组织模型、实体、值对象、领域服务和聚合边界。
- 定义 `adapter/repository` 与 `adapter/port` 接口，表达数据访问和外部能力需求。
- 只依赖领域抽象，不直接依赖 DAO、Redis、HTTP 客户端或其他基础设施实现。
- 业务判断、状态转换和领域约束留在 Domain。

### Case

- 面向用例组织跨领域流程，负责领域服务之间的协调、事务边界和结果组合。
- 不直接访问 DAO、Redis 或远程 Gateway。
- 单一领域、步骤简单且没有跨领域协调时，可以由 Trigger 直接调用 Domain，跳过 Case。
- 涉及多个领域、多个聚合或需要统一编排的流程，必须通过 Case 组织调用。

### Infrastructure

- 在 `adapter/repository` 实现 Domain 的 Repository，在 `adapter/port` 实现 Domain 的 Port。
- Repository 负责调用 `dao`、`redis` 并完成数据映射；Port 负责调用 `gateway` 并完成外部 DTO 转换。
- `config` 只承载技术配置，基础设施不承载领域规则，不绕过 adapter 与 Domain 通信。

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
