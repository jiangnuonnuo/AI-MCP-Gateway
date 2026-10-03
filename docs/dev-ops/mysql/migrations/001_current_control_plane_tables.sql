-- Safe additive migration for the current gateway control plane.
-- This file is intentionally free of DROP/DELETE statements and can run on a
-- populated ai_mcp_gateway_v2 database.

CREATE TABLE IF NOT EXISTS mcp_gateway_tool (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  gateway_id VARCHAR(64) NOT NULL,
  tool_id BIGINT NOT NULL,
  tool_name VARCHAR(128) NOT NULL,
  tool_type VARCHAR(32) NOT NULL DEFAULT 'function',
  tool_description VARCHAR(512) NOT NULL,
  tool_version VARCHAR(16) NOT NULL,
  protocol_id BIGINT NOT NULL,
  protocol_type VARCHAR(32) NOT NULL DEFAULT 'http',
  status TINYINT(1) NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_gateway_tool_name (gateway_id, tool_name),
  UNIQUE KEY uq_gateway_tool_id (gateway_id, tool_id),
  KEY idx_gateway_status (gateway_id, status),
  KEY idx_protocol_key (protocol_type, protocol_id),
  CONSTRAINT chk_gateway_tool_status CHECK (status IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS mcp_datasource (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  datasource_ref VARCHAR(64) NOT NULL,
  datasource_name VARCHAR(128) NOT NULL,
  datasource_type VARCHAR(32) NOT NULL,
  jdbc_url VARCHAR(1024) NOT NULL,
  username VARCHAR(128) NOT NULL,
  password_ciphertext TEXT NOT NULL,
  password_nonce VARCHAR(64) NOT NULL,
  encryption_key_ref VARCHAR(128) NOT NULL,
  status TINYINT(1) NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_datasource_ref (datasource_ref),
  KEY idx_datasource_status (datasource_type, status),
  CONSTRAINT chk_datasource_status CHECK (status IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS mcp_protocol_mysql (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  protocol_id BIGINT UNSIGNED NOT NULL,
  datasource_id BIGINT UNSIGNED NOT NULL,
  sql_text MEDIUMTEXT NOT NULL,
  max_rows INT UNSIGNED NOT NULL DEFAULT 1000,
  max_result_bytes BIGINT UNSIGNED NOT NULL DEFAULT 4194304,
  max_columns SMALLINT UNSIGNED NOT NULL DEFAULT 128,
  timeout_ms INT UNSIGNED NOT NULL DEFAULT 30000,
  status TINYINT(1) NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_mysql_protocol_id (protocol_id),
  KEY idx_mysql_protocol_status (status),
  CONSTRAINT chk_mysql_protocol_status CHECK (status IN (0, 1)),
  CONSTRAINT fk_mysql_protocol_datasource FOREIGN KEY (datasource_id)
    REFERENCES mcp_datasource (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
