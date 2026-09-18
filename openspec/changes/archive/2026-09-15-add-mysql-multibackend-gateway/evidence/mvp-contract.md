# MVP 链路契约

## 范围

首个纵向链路只包含已发布的 MySQL 只读模板 Tool：

`tools/list -> tools/call -> ToolExecutorRouter -> MysqlTemplateExecutor -> JDBC -> MCP`

HTTP Tool 保留兼容回归，但不属于本次 MySQL 首个验收链路；动态 SQL、写操作、控制库持久化和结果导出不在 MVP 范围内。

## Tool 样例

| 字段 | 值 |
| --- | --- |
| Tool 名称 | `warehouse_channel_sales` |
| 执行模式 | `MYSQL_TEMPLATE` |
| 数据源引用 | `warehouse-test`（固定绑定，客户端不可覆盖） |
| 模板版本 | `1`（只读、已发布） |
| 查询范围 | `data_warehouse.fact_order` JOIN `data_warehouse.dim_channel` |
| 查询能力 | 时间范围过滤、按渠道聚合订单数和支付金额 |

## 输入 Schema

```json
{
  "type": "object",
  "properties": {
    "fromTime": { "type": "string", "format": "date-time", "description": "起始时间（含）" },
    "toTime": { "type": "string", "format": "date-time", "description": "结束时间（不含）" },
    "orderStatus": { "type": "string", "description": "订单状态" }
  },
  "required": ["fromTime", "toTime", "orderStatus"],
  "additionalProperties": false
}
```

客户端不得传入 `datasourceRef`、JDBC URL、数据库用户名、密码或原始 SQL；这些字段必须被拒绝或忽略，且不得影响固定绑定。

## 结构化结果样例

```json
{
  "columns": [
    { "name": "channel_id", "type": "INT" },
    { "name": "channel_name", "type": "VARCHAR" },
    { "name": "order_count", "type": "BIGINT" },
    { "name": "pay_amount", "type": "DECIMAL" }
  ],
  "rows": [[1, "Douyin", 12, 1234.56]],
  "rowCount": 1,
  "truncated": false,
  "queryId": "q-..."
}
```

空结果必须返回列信息、`rowCount: 0`、空 `rows` 和 `truncated: false`，不能以异常代替合法空结果。

## 稳定错误码

| 错误码 | 适用场景 |
| --- | --- |
| `INVALID_ARGUMENT` | 缺少必填参数或参数类型不匹配 |
| `SQL_PARAMETER_ERROR` | 未声明、重复或无法绑定的 SQL 参数 |
| `SQL_POLICY_REJECTED` | 写操作、DDL、事务、锁定、文件输出、CALL、多语句或解析失败 |
| `DATASOURCE_UNAVAILABLE` | 数据源不存在、停用或连接失败 |
| `TEMPLATE_NOT_PUBLISHED` | 模板不存在、草稿、停用或版本不可用 |
| `QUERY_TIMEOUT` | 超出服务端执行超时 |
| `RESULT_LIMIT_EXCEEDED` | 超出行数、列数或结果字节上限 |
| `ACCESS_DENIED` | Tool、数据源或执行模式未授权 |

错误响应只返回稳定错误码、请求标识和必要的查询标识，不返回密码、JDBC URL、Authorization、完整异常堆栈或敏感结果。
