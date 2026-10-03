## Why

The gateway currently runs from a manually selected Docker image, so replacing it on the production host is difficult to repeat and risks disturbing the existing MySQL and supporting containers. The deployment needs a controlled replacement flow that preserves those containers and provides a single command for future updates.

## What Changes

- Add a production deployment contract for replacing only the MCP gateway application container.
- Add repeatable deployment scripts that build the current application, preserve existing database and supporting containers, perform a health-checked cutover, and support status and rollback operations.
- Add operator documentation and a configuration template for the host, exposed port, container name, and non-sensitive runtime settings.
- Do not recreate, migrate, or modify existing MySQL data volumes or database schema during gateway updates.

## Capabilities

### New Capabilities

- `gateway-deployment`: Repeatable, health-checked replacement and rollback of the gateway application container while reusing existing infrastructure containers.

### Modified Capabilities

- None.

## Impact

- Affected files are under `docs/dev-ops/` and the application container build context.
- Production Docker state changes only for the gateway application container and its image; MySQL, Redis, networks, volumes, and unrelated containers remain operator-managed resources.
- The deployment command requires Docker and Maven (or a prebuilt application artifact) on the deployment host and must not embed credentials in tracked files.
