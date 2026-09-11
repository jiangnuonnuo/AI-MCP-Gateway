# MySQL SQL Parser Spike

## 目标

验证候选解析器对 MVP 模板语法和失败关闭策略的支持。解析失败一律映射为 `SQL_POLICY_REJECTED`，不进入 JDBC。

## 语法矩阵

| 用例 | 预期 | 说明 |
| --- | --- | --- |
| `SELECT ... WHERE order_time BETWEEN ? AND ?` | 允许 | 参数化单语句 |
| `WITH ... AS (...) SELECT ...` | 允许 | 合法只读 CTE |
| `SELECT ... JOIN ... GROUP BY ...` | 允许 | MVP 渠道聚合模板 |
| `INSERT/UPDATE/DELETE/REPLACE` | 拒绝 | 写操作 |
| `CREATE/ALTER/DROP/TRUNCATE` | 拒绝 | DDL |
| `GRANT/REVOKE` | 拒绝 | DCL |
| `START TRANSACTION/COMMIT/ROLLBACK` | 拒绝 | 事务控制 |
| `SELECT ... FOR UPDATE`、`LOCK TABLES` | 拒绝 | 锁定 |
| `SELECT ... INTO OUTFILE` | 拒绝 | 文件输出 |
| `CALL ...` | 拒绝 | 存储过程/副作用 |
| 两条分号分隔语句 | 拒绝 | 多语句 |
| 不完整或无法识别 SQL | 拒绝 | 失败关闭 |

实现测试会同时断言：允许语句只包含查询类型、占位符数量与声明参数一致，所有拒绝用例在 JDBC 执行前结束。
