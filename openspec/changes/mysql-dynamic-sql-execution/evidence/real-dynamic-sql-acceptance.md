# 动态 SQL 真实验收记录

## 验收入口

- 测试：`cn.bugstack.ai.test.infrastructure.acceptance.MysqlDynamicSqlAcceptanceTest`
- 开关：`OPENSPEC_REAL_MYSQL_ACCEPTANCE=1`
- 命令：`control_db_password=$(docker exec xerina-mysql sh -lc 'printf %s "$MYSQL_ROOT_PASSWORD"'); OPENSPEC_REAL_MYSQL_ACCEPTANCE=1 SPRING_DATASOURCE_PASSWORD="$control_db_password" mvn -q -pl ai-mcp-gateway-app -am -Dtest=cn.bugstack.ai.test.infrastructure.acceptance.MysqlDynamicSqlAcceptanceTest -Dsurefire.failIfNoSpecifiedTests=false test`
- 本地控制面：验收测试通过 `AdminController.saveGatewayConfig` 和 `AdminMysqlController.saveMysqlDynamicBinding` 创建 Gateway 与动态 Tool，再用状态接口启用；断言来自真实控制库的关联记录。测试清理只针对临时验收 Gateway。
- Redis：仅使用测试替身承载会话同步，不替代 Gateway、Tool、协议、数据源和 MySQL 查询链路。

## 专用绑定

| 对象 | 验收值 |
| --- | --- |
| Gateway | `openspec_dynamic_gateway` |
| Tool | `dynamicReadonlyQuery` |
| MySQL protocol_id | 由动态绑定 Repository 从控制库生成（本次运行动态分配） |
| execution_mode | `DYNAMIC_READONLY` |
| 数据源 | `data-warehouse` 的已启用只读数据源 |
| SQL | 外部传入 `SELECT COUNT(*) AS order_count FROM fact_order WHERE order_id <= :maxOrderId` |
| 参数 | `{"maxOrderId":7}` |

测试在执行前通过管理用例创建并在结束后删除上述 Gateway、Tool 和动态协议记录；协议记录的 `sql_text` 保持 `NULL`，数据源引用来自控制面外键。

产品页面也可直接复现同一链路：打开 Tool 绑定，执行模式选择“动态 SQL（调用时传入）”，选择 `data-warehouse`，保存后在 Gateway 列表打开 `xerina_dynamic_sql_acceptance`，即可看到 `dynamicReadonlyQuery`。本地验收还保留了该 Gateway/Tool 作为可视化手工验收资源。

当前本地控制面已保留一组可直接验收的资源：Gateway=`xerina_dynamic_sql_acceptance`、Tool=`dynamicReadonlyQuery`、动态协议=`900005`、Tool 绑定=`66`，Tool 与协议均为启用状态。

## 传输与断言

- SSE HTTP Controller 链路发送 `tools/list` 和 `tools/call`，从 SSE message event 读取 Tool 列表与结构化查询响应。
- Streamable HTTP Controller 链路发送同一组 `tools/list` 和 `tools/call`，对比 JSON 响应中的 Tool 名称、`queryId`、列和行结果。
- `tools/list` 只允许暴露 `sql`、`parameters` 输入契约；数据源引用、JDBC 地址、策略和凭证不得出现。
- `tools/call` 的动态 SQL 只能使用服务端绑定的数据源和资源策略；成功结果必须包含一行 `order_count=7`。
- 实际运行结果：`Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`。
- `data_warehouse.fact_order` 的真实基线为 50,000 行；动态查询 `SELECT COUNT(*) AS order_count FROM fact_order WHERE order_id <= :maxOrderId` 绑定 `maxOrderId=7`，SSE 与 Streamable 均返回 `order_count=7`，`queryId=call-sse`。
- 结果上限路径使用同一 Tool 执行 `SELECT order_id FROM fact_order`，返回稳定成功结果并标记 `truncated=true`、`rowCount=10`；这证明服务端策略生效且没有把完整结果外泄。

脱敏响应摘要：

```json
{
  "tools/list": {"tools": [{"name": "dynamicReadonlyQuery", "inputSchema": {
    "required": ["sql", "parameters"], "additionalProperties": false
  }]},
  "tools/call": {"isError": false, "queryId": "call-sse",
    "columns": [{"label": "order_count", "type": "INT"}],
    "rows": [[7]], "rowCount": 1, "truncated": false}
}
```

## 失败矩阵

动态执行器和领域责任链覆盖写操作、DDL、锁、文件输出、多语句、语法错误、危险超时函数、缺失/多余/非法参数、客户端数据源字段、停用 Tool、停用数据源和结果限制。失败响应只保留稳定错误码，拒绝路径不进入查询端口；JDBC 的连接、PreparedStatement 和 ResultSet 由现有 try-with-resources 路径释放。JDBC 查询超时异常映射由 Infrastructure 测试覆盖，真实 Gateway 验收对 `SLEEP` 超时注入在 SQL 治理责任链处拒绝。

## 脱敏约束

验收输出不得保存密码、Token、Authorization、JDBC URL、完整控制库凭证或不必要的业务结果。该文件只记录绑定标识、最小查询和可重复执行命令；实际响应应在保存前按测试断言过滤敏感字段。

在未设置 `OPENSPEC_REAL_MYSQL_ACCEPTANCE=1` 或控制库/`data-warehouse` 不可用时，本地构建不会伪造真实验收通过状态；普通构建保持跳过，显式打开开关后使用本地 MySQL 容器完成上述真实验收。
