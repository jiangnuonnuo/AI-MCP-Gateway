# MySQL 配置契约架构边界记录

## 观察到的错误

`MysqlConnectionSettings` 和 `MysqlConnectionSettingsRegistry` 原先位于
`ai-mcp-gateway-types/.../types/config`，但它们只被 Infrastructure 生产代码和 Infrastructure
测试引用。前者暴露 JDBC 地址、用户名、运行时密码和连接池限制，后者由
`MysqlDataSourceRepository` 实现并由 `MysqlJdbcGateway` 消费；两者都不是 Domain 模型，也不是
App Config 实现。

原位置会造成两个误导：

1. 将 Infrastructure 内部技术契约看起来像公共跨模块类型；
2. `MysqlConnectionSettingsRegistry` 的旧注释把 Infrastructure 数据源解析误描述为 App Config 适配。

## 修正后的归属

```text
ai-mcp-gateway-types/.../types/config
└── MysqlRuntimeSettings
    └── App Config → Infrastructure 的中立技术上限契约

ai-mcp-gateway-infrastructure/.../infrastructure/mysql
├── MysqlConnectionSettings
└── MysqlConnectionSettingsRegistry
    └── Infrastructure 内部 JDBC 运行时设置与数据源解析契约
```

App Config 仍由 `MysqlConnectionProperties` 实现 `MysqlRuntimeSettings`；
`MysqlDataSourceRepository` 从 `mcp_datasource` 读取并解密数据源配置，合并应用级上限后，
通过 Infrastructure 内部契约提供给 `MysqlJdbcGateway`。

## 不受影响的范围

- 不修改 `mcp_datasource`、`mcp_protocol_mysql` 或其他数据库结构。
- 不修改 MyBatis Mapper、Domain Port、MCP endpoint、工具 Schema 或查询结果结构。
- 不改变密码解密、连接池、只读执行和资源限制行为。

## 验证证据

- 全仓库检索确认旧 `cn.bugstack.ai.types.config.MysqlConnectionSettings*` 引用已移除。
- `MysqlRuntimeSettings` 的唯一生产实现仍为 `MysqlConnectionProperties`。
- 迁移后接口方法签名保持不变，Spring 注入 Bean 名称和实现类保持不变。
- 相关 Mapper XML 仍为合法 XML；数据库表结构未触碰。
- `javac` 对迁移后的无第三方依赖契约接口编译通过，`xmllint` 对全部 Mapper XML 校验通过，`git diff --check` 通过，OpenSpec 严格校验通过。
- 当前环境未安装 Maven，无法在本次环境中执行 Maven 全量测试；该限制不影响本次接口包迁移的静态验证。
- 归档变更自身通过 `openspec validate --archived --strict`；全局归档校验仍有一个既有变更 `2026-09-15-add-mysql-multibackend-gateway` 保留 1 个未完成任务，与本次变更无关。
