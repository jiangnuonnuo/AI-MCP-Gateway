# data_warehouse 只读检查记录

检查目标：本机 `127.0.0.1:3306/data_warehouse`。本记录只保存元数据和聚合统计，不保存账号、密码或连接配置。

## 执行的只读检查

- `SELECT 1`：连接成功。
- `information_schema.TABLES`：确认 10 张业务表均为 `BASE TABLE`。
- `information_schema.COLUMNS`：确认表、列、数据类型、可空性和主键/索引信息。
- 只读 `COUNT` 与 `MIN/MAX` 聚合：确认数据范围和候选 JOIN 规模。
- 只读 JOIN/聚合抽样：`fact_order.channel_id = dim_channel.channel_id`。

未执行 `CREATE`、`INSERT`、`UPDATE`、`DELETE`、`ALTER`、`DROP`、`TRUNCATE` 或其他写操作。

## 可用表清单

| 表 | 行数 | 关键字段 |
| --- | ---: | --- |
| `dim_brand` | 116 | `brand_id`, `brand_name` |
| `dim_category` | 50 | `category_id`, `category_name` |
| `dim_channel` | 8 | `channel_id`, `channel_name` |
| `dim_date` | 1095 | `date_id`, `dt`, `year`, `month` |
| `dim_product` | 2000 | `product_id`, `category_id`, `brand_id`, `supplier_id` |
| `dim_supplier` | 100 | `supplier_id`, `supplier_name` |
| `dim_user` | 5000 | `user_id`, `register_time`, `province` |
| `fact_order` | 50000 | `order_id`, `user_id`, `order_time`, `channel_id`, `pay_amount` |
| `fact_order_item` | 120426 | `order_id`, `product_id`, `category_id`, `brand_id`, `supplier_id` |
| `fact_user_behavior` | 100000 | `user_id`, `product_id`, `behavior_time`, `channel_id` |

## 模板选择

选择 `fact_order` JOIN `dim_channel` 的时间范围聚合模板。该模板引用的表和列均已由上述元数据检查确认，且具备稳定的 JOIN 与聚合语义。没有对 `data_warehouse` 创建测试数据或修改对象。
