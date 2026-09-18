# Gateway 控制面字段矩阵

## `mcp_datasource`

| 字段 | MySQL 类型 | 约束与默认值 | Java 映射 | 责任边界 |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | PK, AUTO_INCREMENT | `Long id` | Infrastructure 标识 |
| `datasource_ref` | `VARCHAR(64)` | NOT NULL, UNIQUE | `String datasourceRef` | Domain 业务引用 |
| `datasource_name` | `VARCHAR(128)` | NOT NULL | `String datasourceName` | 管理展示 |
| `datasource_type` | `VARCHAR(32)` | NOT NULL | `String datasourceType` | 协议适配选择 |
| `jdbc_url` | `VARCHAR(1024)` | NOT NULL；禁止密码参数 | `String jdbcUrl` | Infrastructure 连接配置 |
| `username` | `VARCHAR(128)` | NOT NULL | `String username` | Infrastructure 连接配置 |
| `password_ciphertext` | `TEXT` | NOT NULL；仅密文 | `String passwordCiphertext` | Infrastructure 加解密 |
| `password_nonce` | `VARCHAR(64)` | NOT NULL；每条记录独立 | `String passwordNonce` | Infrastructure 加解密 |
| `encryption_key_ref` | `VARCHAR(128)` | NOT NULL；不保存主密钥 | `String encryptionKeyRef` | App Config/外部密钥引用 |
| `status` | `TINYINT(1)` | NOT NULL, DEFAULT 0, CHECK 0/1 | `Integer status` | Domain 两态生命周期 |
| `create_time` | `DATETIME` | DEFAULT CURRENT_TIMESTAMP | `Date createTime` | 审计时间 |
| `update_time` | `DATETIME` | 自动更新时间 | `Date updateTime` | 审计时间 |

索引：`uq_datasource_ref(datasource_ref)`、`idx_datasource_status(datasource_type,status)`。

## `mcp_protocol_mysql`

| 字段 | MySQL 类型 | 约束与默认值 | Java 映射 | 责任边界 |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | PK, AUTO_INCREMENT | `Long id` | Infrastructure 标识 |
| `protocol_id` | `BIGINT UNSIGNED` | NOT NULL, UNIQUE | `Long protocolId` | 多协议逻辑关联 |
| `datasource_id` | `BIGINT UNSIGNED` | NOT NULL, FK RESTRICT | `Long datasourceId` | 数据源物理关联 |
| `sql_text` | `MEDIUMTEXT` | NOT NULL；Domain 最大 64KB | `String sqlText` | 只读 SQL 协议正文 |
| `max_rows` | `INT UNSIGNED` | NOT NULL, DEFAULT 1000 | `Integer maxRows` | 业务行数上限 |
| `max_result_bytes` | `BIGINT UNSIGNED` | NOT NULL, DEFAULT 4194304 | `Long maxResultBytes` | 业务字节上限 |
| `max_columns` | `SMALLINT UNSIGNED` | NOT NULL, DEFAULT 128 | `Integer maxColumns` | 业务列数上限 |
| `timeout_ms` | `INT UNSIGNED` | NOT NULL, DEFAULT 30000 | `Integer timeoutMs` | 业务超时上限 |
| `status` | `TINYINT(1)` | NOT NULL, DEFAULT 0, CHECK 0/1 | `Integer status` | Domain 两态生命周期 |
| `create_time` | `DATETIME` | DEFAULT CURRENT_TIMESTAMP | `Date createTime` | 审计时间 |
| `update_time` | `DATETIME` | 自动更新时间 | `Date updateTime` | 审计时间 |

索引与约束：`uq_mysql_protocol_id(protocol_id)`、`idx_mysql_protocol_status(status)`、`fk_mysql_protocol_datasource(datasource_id)`；不存在 `mcp_mysql_template`。

## 既有表调整

| 表 | 调整 | Java/Mapper 一致性 |
| --- | --- | --- |
| `mcp_gateway_tool` | `protocol_type VARCHAR(32)`；`status` 默认 0；`UNIQUE(gateway_id,tool_id)`；`INDEX(gateway_id,status)`；`INDEX(protocol_type,protocol_id)` | `McpGatewayToolPO.protocolType/status`；查询返回完整状态与协议类型 |
| `mcp_protocol_mapping` | 增加 `protocol_type VARCHAR(32)`；`UNIQUE(protocol_type,protocol_id,mapping_type,mcp_path)`；`INDEX(protocol_type,protocol_id)` | `McpProtocolMappingPO.protocolType`；HTTP/MySQL 查询按协议键隔离 |
| `mcp_protocol_http` | `UNIQUE(protocol_id)` | 单行读取语义由数据库保证 |

连接池大小、连接获取/校验超时、并发以及应用级资源上限仅由 `gateway.mysql` App Config 提供，不进入控制库表。
