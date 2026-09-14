# 真实 MySQL MCP 调用验收记录

## 验收范围

使用运行时注入的数据库凭证，执行 `MysqlMcpRealIntegrationTest`，验证完整链路：

```text
tools/list -> tools/call -> ToolExecutorRouter -> MysqlTemplateExecutor
  -> MysqlTemplateQueryService -> SQL safety chain -> MysqlJdbcGateway
  -> Hikari/JDBC -> data_warehouse -> MCP structured result
```

测试只执行只读 `SELECT`，不执行 DDL、DML、权限变更或其他会修改目标数仓的操作。

## 真实数据核验

- `data_warehouse.fact_order` 可查询，当前检查到 50,000 条记录。
- `data_warehouse.dim_channel` 可查询，当前检查到 8 条渠道记录。
- `fact_order JOIN dim_channel` 的时间范围和订单状态聚合查询返回非空结果。
- `tools/list` 返回固定模板 Tool 的名称、描述和参数 Schema，不返回 SQL 正文或凭证。
- `tools/call` 返回 `isError=false`、列信息、行数据、行数和 queryId。

## 自动化测试

```text
mvn -q -pl ai-mcp-gateway-app -am \
  -Dtest=MysqlMcpRealIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

测试依赖 `WAREHOUSE_MYSQL_USERNAME`、`WAREHOUSE_MYSQL_PASSWORD` 和可选的
`WAREHOUSE_MYSQL_URL` 运行时注入；未注入凭证时测试跳过，避免误连数据库。

## 权限验证边界

本次验证在测试容器中临时创建了仅授予 `data_warehouse.*` `SELECT` 权限的账号，并在测试结束后删除该账号：

- `SHOW GRANTS` 确认目标库授权不包含 `INSERT`、`UPDATE`、`DELETE`、`CREATE`、`ALTER`、`DROP` 或 `TRUNCATE`。
- 应用层 SQL 责任链拒绝同一条写操作。
- 通过 JDBC 直接执行 `UPDATE ... WHERE 1 = 0` 时，数据库返回权限错误（MySQL `1142` / SQLState `42000`）；因为没有写权限，目标数据未被修改。

因此任务 6.4 的失败关闭和数据库只读权限双重防线均已通过真实 MySQL 验证。
