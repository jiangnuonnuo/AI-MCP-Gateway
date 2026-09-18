# 开发控制库清理与重建

开发验收只清理 `ai_mcp_gateway_v2` 控制库。先执行同目录的 `development-reset.sql`，再写入 HTTP 回归数据和默认禁用的 MySQL 数据源、协议、mapping、Tool 绑定。

该脚本只重建本变更列出的控制面表；若目标控制库还包含能力包、发布、审计或其他未列出的表，必须先建立隔离副本并取得 Xerina 对清理范围的确认，不得把这些表当作隐含的可删除对象。

真实 `data_warehouse` 只作为目标库读取，禁止执行 `DROP`、`TRUNCATE`、`ALTER`、DML 或权限变更。数据源密码必须先由凭证适配器加密，控制库只写入密文、nonce 和外部密钥引用；数据源、协议和 Tool 初始状态均为 `DISABLED`，验收时逐项显式启用。

SQL 保存前只经过 Domain 的 JSqlParser 责任链，不执行 `EXPLAIN`、元数据探测或业务查询。
