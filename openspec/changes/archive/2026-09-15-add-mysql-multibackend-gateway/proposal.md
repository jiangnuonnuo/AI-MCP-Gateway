## Why

当前 Gateway 已能将 HTTP 接口转换为 MCP Tool，但 Tool 执行路径固定依赖 HTTP，无法直接访问真实的 MySQL 数仓。电商分析场景需要通过 MCP 调用经过治理的只读 SQL，既要支持包含 JOIN 和聚合的业务查询模板，也要为授权调用方保留受控的动态查询能力；同时必须把任何写入、DDL、锁定或其他副作用操作挡在执行前。

本变更将 Gateway 从 HTTP-only 扩展为支持 HTTP 与 MySQL/JDBC 的多后端 MCP Gateway，并以本机真实 MySQL `127.0.0.1:3306/data_warehouse` 作为 MVP 验收目标，先打通一个只读模板查询的端到端链路，再逐步增加动态只读 SQL 能力。数据库用户名和密码只允许在运行时注入，不写入规格、代码、日志或版本库。

## What Changes

- 增加后端执行器抽象和按 Tool 后端类型路由的执行流程，保留现有 HTTP Tool 行为。
- 增加 MySQL 数据源配置、独立连接池、健康检查和安全密钥引用能力。
- 增加 MySQL 只读查询模板 Tool，支持参数校验、参数绑定、JOIN/聚合查询和结构化 MCP 结果。
- 增加 SQL 只读安全策略：解析 SQL、拒绝多语句及写操作、DDL、DCL、事务、锁定、文件输出和其他副作用语句。
- 增加查询超时、最大行数、结果大小、并发和调用权限等治理策略。
- 增加 MySQL 查询错误码、调用审计和关键运行指标。
- 在 MVP 阶段使用本机已有的 `data_warehouse` 进行只读验收，先检查实际可用表结构再确定测试模板；不向该库执行 DDL/DML。
- 在 MVP 阶段暂不开放动态 SQL、写操作、数据字典、异步大查询和字段级业务语义能力；为后续阶段保留扩展点。
- **BREAKING** 将执行器入口从 HTTP 专用配置演进为后端无关的 Tool 执行上下文；已有 HTTP Tool 必须保持兼容。

## Capabilities

### New Capabilities

- `multi-backend-tool-execution`: 统一管理 HTTP 和 MySQL 后端 Tool 的注册、发现、路由、执行和结果转换。
- `mysql-readonly-template-query`: 通过 JDBC 执行已发布的只读 MySQL 查询模板，并以 MCP Tool 形式返回结构化结果。
- `mysql-query-governance`: 提供 SQL 只读安全校验、数据源隔离、资源限制、权限控制、错误和审计约束。

### Modified Capabilities

- 无。当前仓库没有已落地的 OpenSpec 主规格；现有 HTTP 行为的兼容要求在新能力规格中定义。

## Impact

- Domain：新增后端类型、执行模式、查询模板、查询策略和 Tool 执行端口；不依赖 JDBC、MyBatis 或具体数据库驱动。
- Case：编排 Tool 配置解析、权限检查、执行器选择、参数校验和结果处理。
- Infrastructure：新增 MySQL/JDBC 执行器、数据源连接池、SQL Parser 适配、安全策略实现、结果映射和审计适配器；HTTP 执行器继续保留。
- API/Trigger：扩展管理请求和 MCP Tool 执行上下文，但不改变 MCP 客户端的基本调用方式。
- 依赖：复用现有 MySQL Connector/J 和 Spring Boot 配置；需要选择并验证 SQL Parser 依赖。
- 数据库：需要新增或调整网关配置模型以保存数据源、MySQL Tool 和模板引用。具体表、字段、索引和迁移方案必须在实现前单独确认，本变更不预先编写 DDL。
- 外部系统：MVP 连接本机 `127.0.0.1:3306/data_warehouse`；验收仅执行 `SELECT` 和必要的只读元数据检查，不创建、修改或删除目标库对象及数据。用户名作为运行时配置项，密码通过环境变量或本地密钥注入；生产密钥管理、数据权限和数据脱敏由后续治理阶段补齐。
