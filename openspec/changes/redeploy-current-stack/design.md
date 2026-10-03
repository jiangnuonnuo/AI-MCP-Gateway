## Context

The repository is a Maven multi-module Java application with static frontend files under `docs/dev-ops/nginx/html` and a MySQL dump under `docs/dev-ops/mysql`. Production currently has separate `ai-mcp-gateway`, `ai-mcp-gateway-nginx`, `mysql`, and `redis` containers on `app-net`; the Nginx container bind-mounts its document root and proxies `/api-gateway/` to the gateway. The existing gateway-only script does not synchronize the frontend or run database migrations.

## Goals / Non-Goals

**Goals:**

- Make the repository checkout the single source for the deployed frontend and backend artifact.
- Back up production database state and apply compatible migrations idempotently.
- Cut over gateway and frontend content with verifiable health checks and rollback metadata.
- Leave infrastructure container identities, networks, and volumes untouched.

**Non-Goals:**

- Recreating MySQL, Redis, Nginx, or unrelated services.
- Loading the repository's destructive full SQL dump into a non-empty production database.
- Changing application authentication or business behavior as part of deployment.

## Decisions

1. **One host-side deployment driver.** A shell driver runs from the deployment bundle, because it can inspect Docker metadata, stage static files, invoke Maven/Docker, and call `docker exec` for MySQL without requiring a new orchestrator. Compose files remain examples and are not used for production cutover.
2. **Staged frontend directory with bind-mount-safe replacement.** The script copies the current static tree into a sibling staging directory, snapshots the existing root, and replaces its contents in place because the Nginx bind mount follows the existing directory inode. Nginx is reloaded in place so its container and bind mount remain unchanged.
3. **Migration ledger inside the target database.** A small deployment ledger records migration basenames. Repository migrations are ordered and executed only when absent from the ledger; the full initialization dump is used only for an empty database and is never run automatically over production rows.
4. **Gateway snapshot before cutover.** The current image, environment, mounts, network, and container name are recorded before stopping the gateway. A failed health check restores the prior container. MySQL and Redis are never passed to removal or lifecycle commands.
5. **Single bundle update entrypoint.** `deploy-current-stack.sh update` performs backup, migration, frontend stage, backend build/cutover, Nginx reload, and verification. `status` reports component revisions without secret values; `rollback` restores the latest snapshot.

## Risks / Trade-offs

- **[Risk]** A repository SQL file may be a full dump rather than a safe migration. → **Mitigation:** require explicit migration files for non-empty databases, reject destructive statements in update mode, and retain a logical backup before every run.
- **[Risk]** Static root replacement can briefly race with Nginx requests. → **Mitigation:** stage completely, retain a restorable copy, replace the bind-mounted contents in one short operation, then reload Nginx and probe `index.html`.
- **[Risk]** Existing runtime environment contains secrets unavailable in a clean bundle. → **Mitigation:** capture the gateway container environment into a mode-600 state file without printing values; allow an operator env file to override non-secret deployment settings.
- **[Risk]** Rollback cannot undo arbitrary application data changes. → **Mitigation:** retain the pre-update SQL backup and report its path; database restore remains an explicit operator action.

## Migration Plan

1. Upload the deployment bundle and operator environment to `/home/ubuntu/gw-deploy`.
2. Run `./deploy-current-stack.sh update`; the script records pre-update container IDs, creates a database dump, applies missing migrations, stages the frontend, builds the current JAR/image, and verifies the public endpoint.
3. Inspect `status` and the public login/API pages. MySQL and Redis container IDs must match the pre-update snapshot.
4. If verification fails, run `rollback`; if database data must be reverted, restore the reported SQL backup explicitly after stopping only the gateway.
