# MySQL 管理工作台验收记录

页面入口：`docs/dev-ops/nginx/html/admin.html`，通过 hash 导航依次打开 `datasource`、`sql-template`、`tool-binding` 三个 View。

| 场景 | 验收结果 |
| --- | --- |
| 数据源列表、搜索、分页和右侧检查器 | 通过；列表在 REST 不可用时保留安全预览数据，真实接口成功后切换为分页响应 |
| 数据源表单密码遮罩、连接测试、停用/删除确认 | 通过；密码不回填，错误码 `DATASOURCE_IN_USE` 映射为解除引用提示 |
| SQL 模板参数与 SQL 编辑器 | 通过；启用记录的 SQL、数据源和参数字段禁用，错误码 `ENABLED_TEMPLATE_IMMUTABLE` 映射为停用/新建提示 |
| Tool 绑定 Gateway/模板筛选、同名提示、启停与删除 | 通过；`TOOL_NAME_CONFLICT` 与资源不可用错误均使用安全 Toast |
| 移动端布局 | 通过；375px 宽度无水平溢出，侧栏导航可横向滚动，详情检查器保持可读 |
| 网络边界 | 通过；前端请求只指向 `/admin/query_*`、`/admin/save_*`、`/admin/change_*`、`/admin/delete_*` 和显式测试接口，不访问控制库 |

响应脱敏检查覆盖密码、Token、Authorization、密文、nonce、密钥引用以及完整 JDBC 地址；页面详情只显示连接摘要和密码“不回显”提示。
