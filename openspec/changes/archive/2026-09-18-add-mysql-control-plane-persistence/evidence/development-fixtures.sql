-- 在 docs/dev-ops/mysql/sql/ai_mcp_gateway_v2.sql 完成后执行。
-- 仅写入 Gateway 控制库；禁止在 data_warehouse 执行。
-- PASSWORD_CIPHERTEXT 与 PASSWORD_NONCE 必须由 DataSourceCredentialCipher 生成并通过安全渠道替换。
USE ai_mcp_gateway_v2;

SET @warehouse_password_ciphertext = '${DATA_WAREHOUSE_PASSWORD_CIPHERTEXT}';
SET @warehouse_password_nonce = '${DATA_WAREHOUSE_PASSWORD_NONCE}';
SET @warehouse_key_ref = 'env:DATA_WAREHOUSE_CREDENTIAL_KEY';

INSERT INTO mcp_datasource
  (datasource_ref, datasource_name, datasource_type, jdbc_url, username,
   password_ciphertext, password_nonce, encryption_key_ref, status)
VALUES
  ('data-warehouse', 'Development data warehouse', 'mysql',
   'jdbc:mysql://127.0.0.1:3306/data_warehouse?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false',
  'root', @warehouse_password_ciphertext, @warehouse_password_nonce, @warehouse_key_ref, 0)
ON DUPLICATE KEY UPDATE
  datasource_name = VALUES(datasource_name),
  datasource_type = VALUES(datasource_type),
  jdbc_url = VALUES(jdbc_url),
  username = VALUES(username),
  password_ciphertext = VALUES(password_ciphertext),
  password_nonce = VALUES(password_nonce),
  encryption_key_ref = VALUES(encryption_key_ref),
  status = 0;

SET @warehouse_datasource_id = (
  SELECT id FROM mcp_datasource WHERE datasource_ref = 'data-warehouse'
);

INSERT INTO mcp_protocol_mysql
  (protocol_id, datasource_id, sql_text, max_rows, max_result_bytes, max_columns, timeout_ms, status)
VALUES
  (900001, @warehouse_datasource_id,
   'SELECT c.channel_name, COUNT(*) AS order_count, SUM(o.pay_amount) AS total_amount FROM fact_order o JOIN dim_channel c ON c.channel_id = o.channel_id WHERE o.order_time >= :fromTime AND o.order_time < :toTime GROUP BY c.channel_name ORDER BY total_amount DESC',
   100, 1048576, 8, 5000, 0)
ON DUPLICATE KEY UPDATE
  datasource_id = VALUES(datasource_id),
  sql_text = VALUES(sql_text),
  max_rows = VALUES(max_rows),
  max_result_bytes = VALUES(max_result_bytes),
  max_columns = VALUES(max_columns),
  timeout_ms = VALUES(timeout_ms),
  status = 0;

INSERT INTO mcp_gateway_tool
  (gateway_id, tool_id, tool_name, tool_type, tool_description, tool_version,
   protocol_id, protocol_type, status)
VALUES
  ('gateway_001', 900001, 'queryDataWarehouseOrderSummary', 'function',
   '按渠道查询数仓订单汇总', '1.0.0', 900001, 'mysql', 0)
ON DUPLICATE KEY UPDATE
  tool_name = VALUES(tool_name),
  tool_description = VALUES(tool_description),
  tool_version = VALUES(tool_version),
  protocol_id = VALUES(protocol_id),
  protocol_type = VALUES(protocol_type),
  status = 0;

DELETE FROM mcp_protocol_mapping
WHERE protocol_type = 'mysql' AND protocol_id = 900001;

INSERT INTO mcp_protocol_mapping
  (protocol_type, protocol_id, mapping_type, parent_path, field_name, mcp_path,
   mcp_type, mcp_desc, is_required, sort_order)
VALUES
  ('mysql', 900001, 'request', NULL, 'fromTime', 'fromTime', 'string', '查询开始时间', 1, 1),
  ('mysql', 900001, 'request', NULL, 'toTime', 'toTime', 'string', '查询结束时间', 1, 2);

-- HTTP 回归数据由主 DDL 脚本提供，HTTP 行只依赖 mcp_protocol_http、
-- mcp_protocol_mapping(protocol_type='http') 和 mcp_gateway_tool(protocol_type='http')。
