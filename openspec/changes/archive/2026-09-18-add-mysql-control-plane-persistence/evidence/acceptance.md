# 验收记录

记录日期：2026-09-18（Asia/Shanghai）

## 离线验证

| 验证项 | 结果 |
| --- | --- |
| Java 17 Reactor 编译 | 通过 |
| 控制面持久化、SQL 安全、协议隔离、凭证密文和 MCP discovery 定向测试 | 22 项通过，0 失败 |
| MySQL/HTTP 同 `protocol_id` 隔离 | 通过 `SessionRepositoryTest` |
| 非法 SQL 在 Repository 写入前拒绝 | 通过 `MysqlProtocolManagementServiceTest` |
| 新协议默认 `DISABLED`、启用协议内容不可原地覆盖 | 通过 `MysqlProtocolRepositoryTest` |
| `tools/list` 不暴露 SQL、数据源引用和内部协议 ID | 通过 `PersistedToolsListHandlerTest` |
| 凭证 AES-GCM 加解密与错误脱敏 | 通过 `DataSourceCredentialCipherTest` |
| 真实控制库 Spring 上下文与 Redis 外部依赖隔离 | 通过 `MysqlControlPlaneAcceptanceTest`；验收测试仅 mock Redis，不改变生产配置 |
| 全量 Maven 测试 | 42 项通过，0 失败，8 项环境门控测试跳过（2 项真实 MySQL 集成测试、6 项真实控制面验收） |
| OpenSpec 严格校验与 `git diff --check` | 通过 |
| 隔离临时控制库执行主 DDL 与 fixtures | 通过；随后已删除临时库，未触碰现有控制库 |
| `data_warehouse.fact_order JOIN dim_channel` 目标 SQL 结构烟测 | root 连接直查通过；真实 MCP/JDBC 验收另有记录 |

## 真实 MySQL 验收

状态：核心链路、故障矩阵、生命周期和敏感信息复核全部通过。

本次按 Xerina 确认的方案 2 清空并重建了本地 Gateway 控制库，仅执行控制库 DDL 和 fixtures；`data_warehouse` 未执行写操作。验收临时使用本地 MySQL `root` 账号，密码通过运行时密钥引用解密，未写入 SQL、日志或 MCP 响应。

| 验收项 | 结果 | 证据 |
| --- | --- | --- |
| 清空重建控制库、写入 HTTP/MySQL fixtures | 通过 | 主 DDL、development fixtures；控制库查询确认 HTTP 启用记录、MySQL 数据源/协议/映射存在 |
| 启用真实数据源、协议和 Tool，执行 `tools/list` | 通过 | `MysqlControlPlaneAcceptanceTest`；响应仅含标准 MCP Tool 字段 |
| `tools/call -> MySQL/JDBC -> data_warehouse` | 通过 | `MysqlControlPlaneAcceptanceTest`；结构化结果包含渠道、订单数和金额，且 `isError=false` |
| `data_warehouse` 数据未被写入 | 通过 | 验收后 `fact_order=50000`、`dim_channel=8`，与验收前一致 |
| root 账号参数绑定、行/字节/列/超时边界与 SQL 只读策略 | 通过 | `MysqlControlPlaneAcceptanceTest` 验证结果行数/列数边界、未截断结果、参数绑定，以及 SQL/数据源覆盖参数被拒绝；root 账号权限不作为本变更门槛 |
| 不存在表/列、无权限、停用、网络故障 | 通过 | `MysqlControlPlaneAcceptanceTest` 注入临时协议/数据源；调用只返回稳定错误码，未返回 JDBC、SQL、账号或内部异常正文 |
| 非法 SQL/注入/多语句/写操作在写入前拒绝 | 通过 | `MysqlControlPlaneAcceptanceTest` 覆盖 malformed、注入、多语句、写操作、DDL、DCL、危险函数和参数契约；控制库临时协议写入数为 0，目标表行数保持不变；另以绑定参数回显验证注入载荷按数据处理 |
| 两态启停与新协议重新绑定 | 通过 | `MysqlControlPlaneAcceptanceTest` 逐项切换 Tool、协议、数据源状态；使用新 `protocol_id` 重绑后调用成功，原启用协议 SQL 和状态保持不变 |
| 日志、异常、控制库和 MCP 响应敏感信息复核 | 通过 | 验收断言控制库仅保存凭证密文和密钥引用，JDBC URL 不含密码/Authorization 参数；MCP 响应和运行日志不含密码、密钥、Authorization 或完整敏感 URL |

验收命令通过环境变量提供密钥材料，明文密码未进入命令、SQL、日志或本记录。
