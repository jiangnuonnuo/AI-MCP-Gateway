## Purpose

This capability keeps the production console and gateway runtime aligned with the current repository while allowing safe, repeatable updates that reuse existing infrastructure and preserve operator data.

## ADDED Requirements

### Requirement: Current frontend is deployed atomically

The deployment SHALL stage the current repository frontend files, retain a timestamped backup of the previous document root, and make the staged files visible through the existing Nginx container only after the copy completes.

#### Scenario: Frontend replacement succeeds

- **WHEN** the deployment command receives a valid frontend source and the copy completes
- **THEN** the public root serves the current repository `index.html` and all referenced assets, and a previous-root backup is retained

#### Scenario: Frontend copy fails

- **WHEN** staging or copying any frontend file fails
- **THEN** the command exits non-zero and the previously served document root remains available

### Requirement: Current backend replaces only the gateway container

The deployment SHALL build or accept the current gateway application image, reuse the existing gateway network and runtime environment, and replace only the configured gateway container after a health check succeeds.

#### Scenario: Backend update succeeds

- **WHEN** the new image starts and the configured health endpoint returns a successful response within the timeout
- **THEN** the gateway container runs the new image on the existing network and the previous image/runtime metadata remains available for rollback

#### Scenario: Backend health check fails

- **WHEN** the new container fails to become healthy within the timeout
- **THEN** the command exits non-zero, restores the previous gateway container when available, and does not remove or restart MySQL or Redis

### Requirement: Database migrations are backed up and applied incrementally

The deployment SHALL create a logical backup of the configured MySQL database before applying repository migration scripts, record each successfully applied migration, and never execute a destructive full schema dump against the production database during an update.

#### Scenario: Missing migration is applied

- **WHEN** a repository migration has not been recorded as applied
- **THEN** the migration runs once, its name is recorded, and existing business rows are preserved

#### Scenario: Migration fails

- **WHEN** a migration returns a non-zero result
- **THEN** the deployment stops before switching the new application live and reports the backup path and failed migration without deleting the database

### Requirement: Infrastructure containers and volumes are preserved

The deployment SHALL reuse the existing MySQL, Redis, Docker network, and Nginx containers and SHALL NOT issue commands that remove, recreate, or bring down those resources.

#### Scenario: Full update completes

- **WHEN** frontend, database, backend, and Nginx verification complete
- **THEN** the MySQL and Redis container IDs and their data volume mappings are unchanged

### Requirement: Updates and rollback are repeatable

The deployment SHALL provide update, status, and rollback operations that do not accumulate duplicate gateway or Nginx containers and that can restore the prior frontend, database backup metadata, and gateway runtime when verification fails.

#### Scenario: Repeated update

- **WHEN** the update operation is run twice with the same source revision
- **THEN** one active gateway container and one active Nginx container remain, and the operation reports the current deployment state

#### Scenario: Rollback

- **WHEN** an operator invokes rollback with a retained deployment snapshot
- **THEN** the previous frontend and gateway runtime are restored and the command verifies the public health endpoint before returning success
