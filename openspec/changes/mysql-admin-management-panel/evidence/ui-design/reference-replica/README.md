# 参考图复刻版产品图

这一版直接复刻 `docs/product/img.png` 的构图与背景，不再做独立风格探索：

- 使用同一张深空背景构图，包含左下角星球、左侧弧形光幕、顶部波纹和右上角轨道弧线。
- 保留参考图的左侧导航、顶部搜索区、顶部大视觉区、四张指标卡、左侧注册表与右侧详情检查器。
- 各页面只替换业务标题、统计数字、列表记录和详情内容。

## 页面图

- [控制台](./dashboard.png)
- [网关列表](./gateway-list.png)
- [网关工具](./gateway-tool.png)
- [HTTP 协议](./gateway-protocol.png)
- [数据源](./datasource.png)
- [SQL 模板](./sql-template.png)
- [Tool 绑定](./tool-binding.png)
- [认证配置](./gateway-auth.png)
- [网关测试](./gateway-test.png)

## 原型

[admin-layout-replica.html](./admin-layout-replica.html)

通过 hash 切换页面，例如：

```text
admin-layout-replica.html#sql-template
admin-layout-replica.html#datasource
admin-layout-replica.html#tool-binding
```
