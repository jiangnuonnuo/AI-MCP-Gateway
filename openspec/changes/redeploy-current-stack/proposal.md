## Why

The production endpoint is serving an older frontend and runtime image than the current repository, so the deployed console does not match the code Xerina asked to release. The deployment flow must replace the complete current application surface while preserving the existing MySQL and Redis services and their data.

## What Changes

- Deploy the current repository frontend files to the production Nginx document root.
- Build and run the current gateway backend image, preserving the existing runtime secrets, network, and service bindings.
- Back up the production MySQL database before applying only the schema/data migrations required by the current backend and console.
- Reload the existing Nginx container and verify the public frontend, login API, backend API, and database tables.
- Provide one repeatable update command plus status and rollback commands for frontend, backend, and database deployment state.
- Preserve MySQL, Redis, their volumes, and unrelated containers; do not run an infrastructure-wide compose down or rebuild.

## Capabilities

### New Capabilities

- `full-stack-deployment`: Repeatable deployment and rollback of the current frontend, gateway backend, and compatible database migrations while reusing existing infrastructure.

### Modified Capabilities

- None.

## Impact

- Affects `docs/dev-ops/` deployment assets, the Nginx static files, the gateway application image, and the production `ai_mcp_gateway_v2` schema.
- Adds a deployment state directory containing frontend/database backups and the prior gateway runtime metadata; it must remain untracked and permission restricted.
- Public behavior changes to the current repository frontend and backend API implementation; existing gateway data and infrastructure remain available.
