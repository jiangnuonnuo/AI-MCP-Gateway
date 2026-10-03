## Purpose

Provide operators with a repeatable way to replace the MCP gateway application on a Docker host while preserving the existing MySQL and supporting infrastructure and keeping a recoverable previous version.

## ADDED Requirements

### Requirement: Gateway-only deployment
The deployment MUST build and start the current gateway application as a Docker container without recreating, deleting, or changing existing MySQL containers, data volumes, or database schema.

#### Scenario: Existing infrastructure is reused
- **WHEN** an operator runs the deployment against a host with an existing MySQL container and its network
- **THEN** the script discovers and reuses the existing network and database endpoint, and no MySQL container or volume lifecycle command is executed

### Requirement: Health-checked cutover
The deployment MUST verify that the replacement gateway is reachable on the configured host port and that its application health endpoint responds successfully before declaring the update complete.

#### Scenario: Replacement becomes healthy
- **WHEN** the new container starts and its health endpoint returns a successful response within the configured timeout
- **THEN** the script reports success and leaves the replacement container running with the configured restart policy

#### Scenario: Replacement fails health verification
- **WHEN** the new container exits or does not become healthy before the timeout
- **THEN** the script reports failure, keeps the previous image/container recoverable, and exits non-zero without touching infrastructure containers

### Requirement: Repeatable update command
The deployment MUST provide a single repeatable command that builds or accepts the current application artifact, replaces the gateway container, and can be run again without manual cleanup.

#### Scenario: Re-running an update
- **WHEN** an operator runs the update command more than once with the same source and configuration
- **THEN** each run converges to one running gateway container with the requested image and port mapping, without accumulating orphan gateway containers

### Requirement: Rollback
The deployment MUST retain the previously active gateway image reference and provide a rollback command that restores it using the same network, port, and runtime configuration.

#### Scenario: Rollback after failed update
- **WHEN** an operator invokes rollback after a failed or unhealthy update
- **THEN** the prior gateway image is started and health-checked, while MySQL and other infrastructure remain unchanged

### Requirement: Secret-safe configuration
The deployment MUST read passwords, API keys, and encryption keys from an ignored environment file or runtime environment and MUST NOT write them to tracked scripts, logs, or command output.

#### Scenario: Missing secret
- **WHEN** a required secret is absent
- **THEN** the script fails before replacing the running gateway and identifies only the missing variable name
