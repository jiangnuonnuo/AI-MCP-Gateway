## Context

The project uses `Trigger/API → Case → Domain ← Infrastructure`, with App Config acting as the composition root. `MysqlConnectionProperties` is the Spring `@ConfigurationProperties` implementation in App Config. `MysqlDataSourceRepository` reads and decrypts `mcp_datasource` records, combines them with application-level runtime limits, and supplies the JDBC adapter with a technical connection view.

The current shared package contains three interfaces. Only `MysqlRuntimeSettings` crosses the App/Infrastructure module boundary. `MysqlConnectionSettings` and `MysqlConnectionSettingsRegistry` are referenced by Infrastructure production code and Infrastructure-focused tests only. The registry documentation also describes an App Config responsibility although its implementation is `MysqlDataSourceRepository`.

## Goals / Non-Goals

**Goals:**

- Make package ownership match actual dependency direction and responsibility.
- Keep the App Config bridge free of JDBC credentials and target datasource state.
- Keep runtime credentials and JDBC-specific connection views inside Infrastructure.
- Document the observed architecture error so future changes do not put Infrastructure-only contracts into the shared types module.
- Preserve all public behavior, persistence structures, MCP behavior, and Domain interfaces.

**Non-Goals:**

- Do not change `mcp_datasource` or `mcp_protocol_mysql` schema.
- Do not change SQL execution, credential encryption, connection-pool behavior, or MCP responses.
- Do not move `MysqlRuntimeSettings`; it remains the neutral contract implemented by App Config.

## Decisions

### 1. Keep the App Config bridge in `types/config`

`MysqlRuntimeSettings` is implemented by `MysqlConnectionProperties` and consumed by `MysqlDataSourceRepository`. Keeping this interface in the dependency-neutral `types` module prevents Infrastructure from importing the App module while allowing the composition root to provide application-level limits. Moving it into Domain would leak technical configuration into business semantics; moving it into App would reverse the module dependency.

### 2. Move Infrastructure-only contracts to `infrastructure/mysql`

`MysqlConnectionSettings` contains JDBC address, username, runtime credential access, and pool/query limits. `MysqlConnectionSettingsRegistry` resolves these settings by a datasource reference. Both are used only by `MysqlDataSourceRepository` and `MysqlJdbcGateway`, so they belong to the Infrastructure technical package rather than the cross-module types module.

The move is package-only: the interfaces and method signatures remain unchanged, and the repository/gateway wiring continues to use the same Spring bean.

### 3. Record the boundary rule in architecture documentation

The architecture documentation will state that `types` may hold stable contracts shared across modules, while JDBC credentials, datasource resolution, connection pools, and other adapter-internal contracts belong to Infrastructure. App Config classes and `@ConfigurationProperties` remain under `ai-mcp-gateway-app/.../config`.

### 4. Do not change database or Domain boundaries

The `mcp_datasource` PO/DAO/Mapper and Domain ports remain unchanged. The Domain continues to depend only on `IMysqlDataSourceRegistry` and `IMysqlQueryPort`; it never sees `MysqlConnectionSettings` or runtime credentials.

## Risks / Trade-offs

- [Risk] Import updates could leave a stale reference in tests or configuration. → Run repository-wide symbol search, XML/Java compilation checks available in the environment, and focused tests where the build tool is available.
- [Risk] A future feature may incorrectly place another adapter-only contract in `types`. → Add the explicit package ownership rule to `docs/architecture/README.md` and the change evidence.
- [Risk] Moving the interfaces can be mistaken for a runtime or schema migration. → Keep signatures and bean wiring unchanged and verify `git diff` contains no DDL or MCP protocol changes.
