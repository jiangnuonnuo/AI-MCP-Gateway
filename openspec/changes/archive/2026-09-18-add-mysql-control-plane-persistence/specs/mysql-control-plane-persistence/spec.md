## Purpose

为多数据源只读 SQL Tool 建立轻量、可扩展的 Gateway 控制面。数据源连接信息和 MySQL SQL 协议来自控制库，MCP Tool 身份和请求映射复用既有链路，SQL 在 Domain 使用 JSqlParser 本地校验后才能保存，并以真实 `data_warehouse` 完成验收。

## ADDED Requirements

### Requirement: The control plane SHALL persist generic datasource records

Gateway MUST persist each datasource reference, type, JDBC address, username, encrypted credential material, credential metadata and enabled status in `mcp_datasource`. Pool sizing, connection acquisition timeout, validation timeout, concurrency and other runtime technical settings MUST remain in App Config. Plaintext passwords and decryption master keys MUST NOT be persisted or logged.

#### Scenario: Multiple datasource types share one registry

- **WHEN** administrators add two datasource records with different references and types
- **THEN** each record can be selected by a protocol implementation without adding a new table per database engine or changing fixed Java Config

#### Scenario: Disabled datasource fails closed

- **WHEN** a MySQL protocol points to a datasource whose status is `DISABLED`
- **THEN** the bound Tool is absent from `tools/list` and cannot execute

### Requirement: The MySQL protocol SHALL be the persisted SQL template

`mcp_protocol_mysql` MUST store `protocol_id`, `datasource_id`, read-only `sql_text`, query limits and status. The design MUST NOT introduce a separate `mcp_mysql_template` table. MCP Tool name, description, version and input mapping MUST continue to come from existing Gateway Tool and protocol mapping records.

#### Scenario: Restart restores a persisted protocol

- **WHEN** an enabled datasource, MySQL protocol and Gateway Tool binding exist before restart
- **THEN** the same Tool is discoverable and callable after restart without a Config factory or in-memory Registry creating a replacement

#### Scenario: Missing protocol fails closed

- **WHEN** a Gateway Tool points to a missing or unreadable MySQL protocol record
- **THEN** Gateway returns a stable configuration error and never falls back to a fixed SQL string or global Registry entry

### Requirement: SQL save SHALL be validated locally before persistence

Every MySQL SQL insert or content change MUST pass the Domain JSqlParser safety chain before persistence. Validation MUST reject malformed SQL, multiple statements, non-read-only statements, DDL, DCL, transaction control, locks, file output, forbidden functions, unsupported syntax, length violations and request-mapping/placeholder mismatches. Validation MUST NOT execute `EXPLAIN`, metadata queries or business SQL.

#### Scenario: Unsafe SQL is rejected before insert

- **WHEN** an administrator submits malformed SQL, multiple statements, a write, DDL, DCL, transaction, lock, file output or forbidden function
- **THEN** the request fails before the control-plane row is written and no target-database query executes

#### Scenario: Placeholder mapping is exact

- **WHEN** named placeholders and flat request mappings differ by missing, duplicate, undeclared or incompatible parameters
- **THEN** Gateway returns a stable SQL parameter validation error before persistence

### Requirement: Datasource, protocol and Tool lifecycle SHALL contain only two states

Persisted datasource records, MySQL protocol records and Gateway Tool bindings MUST use only `ENABLED` and `DISABLED`, default to disabled, and never expose draft, pending, published or deprecated states.

#### Scenario: Disabled records remain administrable but inert

- **WHEN** a locally validated datasource, protocol or Tool is stored as `DISABLED`
- **THEN** it remains queryable for administration but is not exposed or executed

#### Scenario: Enable and disable are immediate

- **WHEN** an administrator changes a validated record between `DISABLED` and `ENABLED`
- **THEN** subsequent discovery and calls observe the new state without a separate publish operation

### Requirement: Existing Gateway Tool and mapping records SHALL carry protocol context

`mcp_gateway_tool` MUST carry a sufficiently sized `protocol_type`, status and gateway-scoped tool identity. `mcp_protocol_mapping` MUST carry `protocol_type` and resolve mappings by `(protocol_type, protocol_id)` so HTTP and MySQL IDs cannot collide. `mcp_protocol_http.protocol_id` MUST be unique because the existing repository reads one row.

#### Scenario: HTTP and MySQL IDs may coexist

- **WHEN** HTTP and MySQL protocols use the same numeric protocol ID
- **THEN** their mappings and Tool lookups remain isolated by protocol type

### Requirement: MCP discovery SHALL be gateway-bound

`tools/list` MUST read enabled Tool rows for the requested Gateway, branch on `protocol_type`, and for MySQL require enabled `mcp_protocol_mysql` and `mcp_datasource` records. It MUST NOT enumerate all SQL protocols globally. The response MUST expose only standard MCP `name`, `description` and `inputSchema`.

#### Scenario: Only the requested Gateway is listed

- **WHEN** two Gateways bind different MySQL Tools
- **THEN** each `tools/list` response contains only its own enabled bindings

#### Scenario: Sensitive control-plane fields stay private

- **WHEN** a MySQL Tool is listed
- **THEN** SQL text, JDBC URL, username, ciphertext, datasource ID and protocol ID are absent from the MCP response

### Requirement: MCP calls SHALL use a fixed persisted binding

`tools/call` MUST resolve `(gateway_id, tool_name)` to one enabled protocol and datasource, then reuse the existing parameter binder and MySQL executor. Clients MUST provide only declared business arguments and MUST NOT override SQL, datasource reference, JDBC address or credentials.

#### Scenario: Real target data is queried

- **WHEN** an enabled Tool is called with valid declared arguments
- **THEN** Gateway executes the persisted read-only SQL against its stored datasource and returns the structured result

#### Scenario: Runtime target errors are sanitized

- **WHEN** the safe persisted SQL refers to a missing table/column, lacks permission or cannot reach the target
- **THEN** the call fails with a stable sanitized error and does not alter control-plane state

### Requirement: Existing HTTP behavior SHALL remain compatible

The redesign MUST preserve existing HTTP Tool discovery, request mapping, parameter conversion and response structure. HTTP rows MUST NOT depend on MySQL tables.

#### Scenario: HTTP regression survives a clean reset

- **WHEN** the development control database is rebuilt with existing HTTP fixtures and MySQL records
- **THEN** HTTP Tools continue through the existing HTTP protocol chain

### Requirement: Development acceptance SHALL preserve real business data

Development MAY clear and recreate only the Gateway control database. Real `data_warehouse` schema and data MUST be preserved and used as a read-only target.

#### Scenario: Clean control plane performs a real call

- **WHEN** a datasource, MySQL protocol and Tool binding are inserted and enabled after local validation
- **THEN** `tools/list` followed by `tools/call` returns the expected structured result from `data_warehouse`

### Requirement: Persistence failures SHALL use the shared exception contract

DAO, Mapper and Repository failures MUST be converted at the Infrastructure boundary to the shared `ai-mcp-gateway-types` exception contract. No Infrastructure-specific exception package may be introduced, and driver SQL text, credentials and stack traces MUST NOT reach MCP clients.

#### Scenario: Control-plane database failure is sanitized

- **WHEN** a connection, mapping, uniqueness or foreign-key error occurs
- **THEN** the caller receives a stable configuration error without JDBC addresses, credentials or driver-specific details
