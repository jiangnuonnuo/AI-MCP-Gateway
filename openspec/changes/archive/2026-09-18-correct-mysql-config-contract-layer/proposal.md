## Why

The shared `types/config` package currently contains two MySQL connection contracts that are used only inside Infrastructure. This obscures the boundary between App Config, cross-module contracts, and Infrastructure technical coordination, and one contract's documentation incorrectly describes the provider as application startup configuration.

## What Changes

- Keep `MysqlRuntimeSettings` as the neutral App Config-to-Infrastructure contract for application-level technical limits.
- Move `MysqlConnectionSettings` and `MysqlConnectionSettingsRegistry` into the Infrastructure MySQL technical package.
- Update imports, tests, and documentation to reflect the corrected ownership.
- Record the package-boundary error and the rule for future configuration contracts in the architecture documentation and this change's evidence.
- Preserve all runtime behavior, database schemas, MCP contracts, and Domain APIs.

## Capabilities

This is a pure architecture/documentation refactor. It changes no externally observable capability, so the change opts out of delta specs with `skip_specs: true`.

## Impact

- `ai-mcp-gateway-types`: one shared configuration contract remains.
- `ai-mcp-gateway-infrastructure`: owns the JDBC runtime settings and provider contracts.
- `ai-mcp-gateway-app`: continues to provide `@ConfigurationProperties` and Bean assembly without changing the runtime contract.
- Tests and architecture documentation are updated; no DDL, Mapper, MCP endpoint, or query behavior changes.

Evidence: [architecture-boundary.md](evidence/architecture-boundary.md)
