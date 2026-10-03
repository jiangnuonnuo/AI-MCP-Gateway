## Context

The repository already contains Docker Compose examples for the application and for MySQL/Redis, but those files model infrastructure lifecycle together and use a registry image. Production replacement must therefore discover the currently running gateway container and its Docker network, leave infrastructure untouched, and preserve the previous image for rollback. The application uses the `dev` profile by default, listens on 8797, and exposes its API under `/api-gateway`.

## Goals / Non-Goals

**Goals:**

- Build the current application and package it as a versioned local Docker image.
- Discover the existing gateway network and infrastructure service names from Docker metadata.
- Replace one gateway container with health and timeout checks.
- Preserve previous image metadata for rollback and make updates idempotent.
- Keep secrets in an untracked environment file or runtime environment.

**Non-Goals:**

- Creating or migrating MySQL schema.
- Replacing MySQL, Redis, Nginx, or any unrelated container.
- Changing application business logic or public API contracts.
- Introducing an orchestration platform beyond Docker and the existing host.

## Decisions

1. **Host-side shell driver with Docker CLI.** A shell script is directly runnable on the target Ubuntu host and can inspect existing containers. Compose alone cannot safely discover and preserve an arbitrary pre-existing container topology. The script may invoke Maven and `docker build`; operators may also provide a prebuilt image through configuration.
2. **Blue/green-style temporary name with cutover.** The new container is started under a temporary name, attached to the discovered network, and checked through the configured host port after the old container is stopped. The previous image tag and runtime metadata are saved before removal. This keeps rollback possible without touching infrastructure.
3. **Stable application port contract.** The container listens on 8797; the host mapping defaults to 8797 and is configurable. Health checking uses `/api-gateway/admin/query_gateway_config_list` with a configurable path so deployments can adapt to the existing gateway endpoint without modifying application code.
4. **Infrastructure discovery, not recreation.** The script identifies the current gateway container by configurable name and obtains its connected network and environment. It never runs `docker compose down`, removes volumes, or issues lifecycle commands for MySQL/Redis.
5. **Secret-safe env file.** `.env.gateway` is explicitly ignored and loaded by the script. The script validates variable presence and avoids echoing values; tracked examples contain placeholders only.

## Risks / Trade-offs

- **[Risk]** The existing container name, network, or port differs from defaults. → **Mitigation:** all identifiers are configurable and discovery fails with an actionable message instead of guessing.
- **[Risk]** The app has no dedicated actuator health endpoint. → **Mitigation:** use the configured API root as the default probe and allow `GATEWAY_HEALTH_PATH` override.
- **[Risk]** Stopping the old container before the new host port can bind causes a short interruption. → **Mitigation:** start and validate on a temporary port first, then perform the smallest possible cutover; retain the old image for immediate rollback.
- **[Risk]** Remote deployment credentials are exposed in shell history. → **Mitigation:** deployment scripts do not contain or transmit credentials; use SSH keys or an interactive secret mechanism outside tracked files.

## Migration Plan

1. Copy the repository (or deployment bundle) and `.env.gateway` to the Ubuntu host.
2. Run `./docs/dev-ops/deploy-gateway.sh install` once to discover the existing gateway and validate Docker connectivity.
3. Run `./docs/dev-ops/deploy-gateway.sh update`; verify the health result and public endpoint.
4. If verification fails, run `./docs/dev-ops/deploy-gateway.sh rollback` and inspect the saved container logs.
5. Future updates repeat step 3; infrastructure containers are not restarted.

## Open Questions

None that affect the contract. The exact existing gateway container name and network are discovered at installation time or supplied in `.env.gateway`.
