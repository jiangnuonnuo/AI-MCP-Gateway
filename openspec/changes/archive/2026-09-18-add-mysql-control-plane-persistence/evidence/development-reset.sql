-- 仅用于开发环境重建 Gateway 控制库；禁止在 data_warehouse 执行。
CREATE DATABASE IF NOT EXISTS ai_mcp_gateway_v2 CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE ai_mcp_gateway_v2;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS mcp_protocol_mysql;
DROP TABLE IF EXISTS mcp_datasource;
DROP TABLE IF EXISTS mcp_protocol_mapping;
DROP TABLE IF EXISTS mcp_protocol_http;
DROP TABLE IF EXISTS mcp_gateway_tool;

CREATE TABLE mcp_gateway_tool (
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
  UNIQUE KEY uq_tool_name (gateway_id, tool_name),
  UNIQUE KEY uq_gateway_tool_id (gateway_id, tool_id),
  KEY idx_gateway_status (gateway_id, status),
  KEY idx_protocol_key (protocol_type, protocol_id),
  CONSTRAINT chk_gateway_tool_status CHECK (status IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE mcp_protocol_http (
  id BIGINT NOT NULL AUTO_INCREMENT,
  protocol_id BIGINT NOT NULL,
  http_url VARCHAR(512) NOT NULL,
  http_method VARCHAR(16) NOT NULL DEFAULT 'POST',
  http_headers TEXT,
  timeout INT DEFAULT 30000,
  retry_times TINYINT DEFAULT 0,
  status TINYINT(1) NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_protocol_id (protocol_id),
  CONSTRAINT chk_protocol_http_status CHECK (status IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE mcp_protocol_mapping (
  id BIGINT NOT NULL AUTO_INCREMENT,
  protocol_id BIGINT NOT NULL,
  protocol_type VARCHAR(32) NOT NULL DEFAULT 'http',
  mapping_type VARCHAR(32) NOT NULL,
  parent_path VARCHAR(256),
  field_name VARCHAR(128) NOT NULL,
  mcp_path VARCHAR(256) NOT NULL,
  mcp_type VARCHAR(32) NOT NULL,
  mcp_desc VARCHAR(512),
  is_required TINYINT(1) NOT NULL DEFAULT 0,
  sort_order INT DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_protocol_mapping_key (protocol_type, protocol_id, mapping_type, mcp_path),
  KEY idx_protocol_mapping_key (protocol_type, protocol_id),
  KEY idx_mapping_type (mapping_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE mcp_datasource (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE mcp_protocol_mysql (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET FOREIGN_KEY_CHECKS = 1;
