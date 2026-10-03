## 1. Deployment contract and configuration

- [x] 1.1 Add the gateway deployment script with configurable container name, host/container ports, network discovery, secret validation, and safe command logging; verify `shellcheck` or `bash -n` succeeds.
- [x] 1.2 Add an ignored environment template and deployment README covering required variables, first install, update, status, and rollback; verify no real credentials occur in tracked files.

## 2. Build and replacement flow

- [x] 2.1 Implement Maven/package and Docker image build steps with explicit image tags and failure handling; verify the image can be built from the application Dockerfile.
- [x] 2.2 Implement gateway-only replacement that snapshots the prior image and runtime metadata, reuses the discovered network, and never invokes infrastructure lifecycle commands; verify command traces contain no MySQL/Redis removal or down operations.
- [x] 2.3 Implement health polling, timeout cleanup, and non-zero failure behavior; verify an unhealthy replacement preserves rollback metadata and leaves infrastructure untouched.

## 3. Rollback and verification

- [x] 3.1 Implement idempotent status and rollback commands using the saved image/configuration; verify a second update does not accumulate gateway containers.
- [x] 3.2 Run local static checks (`bash -n`, `git diff --check`) and a Docker/Maven dry-run or build where available; document remote verification prerequisites when SSH access is unavailable.
