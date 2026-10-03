#!/usr/bin/env bash
set -Eeuo pipefail

# This driver deploys the repository frontend, gateway image, and additive SQL
# migrations as one controlled operation. It never manages infrastructure
# lifecycle; MySQL, Redis, the Docker network, and Nginx are reused in place.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [[ -d "${SCRIPT_DIR}/current/ai-mcp-gateway-app" ]]; then
  DEFAULT_SOURCE_ROOT="${SCRIPT_DIR}/current"
  DEFAULT_ENV_FILE="${SCRIPT_DIR}/.env.current-stack"
else
  DEFAULT_SOURCE_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
  DEFAULT_ENV_FILE="${SCRIPT_DIR}/.env.gateway"
fi
SOURCE_ROOT="${DEPLOY_SOURCE_ROOT:-${DEFAULT_SOURCE_ROOT}}"
ENV_FILE="${DEPLOY_ENV_FILE:-${DEFAULT_ENV_FILE}}"
STATE_DIR="${DEPLOY_STATE_DIR:-${SCRIPT_DIR}/.full-stack-state}"
FRONTEND_SOURCE="${FRONTEND_SOURCE:-${SOURCE_ROOT}/docs/dev-ops/nginx/html}"
MIGRATIONS_DIR="${MIGRATIONS_DIR:-${SOURCE_ROOT}/docs/dev-ops/mysql/migrations}"
NGINX_ROOT="${NGINX_ROOT:-/opt/ai-mcp-gateway/nginx/html}"
PUBLIC_BASE_URL="${PUBLIC_BASE_URL:-http://127.0.0.1:8088}"

GATEWAY_CONTAINER="${GATEWAY_CONTAINER:-ai-mcp-gateway}"
GATEWAY_IMAGE="${GATEWAY_IMAGE:-ai-mcp-gateway}"
GATEWAY_HOST_PORT="${GATEWAY_HOST_PORT:-8797}"
GATEWAY_CONTAINER_PORT="${GATEWAY_CONTAINER_PORT:-8797}"
GATEWAY_BIND_ADDRESS="${GATEWAY_BIND_ADDRESS:-127.0.0.1}"
GATEWAY_NETWORK="${GATEWAY_NETWORK:-}"
GATEWAY_HEALTH_PATH="${GATEWAY_HEALTH_PATH:-/api-gateway/admin/query_gateway_config_list}"
GATEWAY_HEALTH_TIMEOUT="${GATEWAY_HEALTH_TIMEOUT:-120}"
NGINX_CONTAINER="${NGINX_CONTAINER:-ai-mcp-gateway-nginx}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-mysql}"
MYSQL_DATABASE="${MYSQL_DATABASE:-ai_mcp_gateway_v2}"
SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-dev}"
SERVER_PORT="${SERVER_PORT:-${GATEWAY_CONTAINER_PORT}}"
PREVIOUS_CONTAINER="${GATEWAY_CONTAINER}.previous"
RUNTIME_ENV_FILE="${STATE_DIR}/runtime.env"
LATEST_RUN_FILE="${STATE_DIR}/latest-run"

log() { printf '[stack] %s\n' "$*"; }
fail() { printf '[stack] ERROR: %s\n' "$*" >&2; exit 1; }
container_exists() { docker container inspect "$1" >/dev/null 2>&1; }

# Configuration is loaded from an untracked file; values are never printed.
load_env() {
  if [[ -f "${ENV_FILE}" ]]; then
    set -a
    # shellcheck disable=SC1090
    source "${ENV_FILE}"
    set +a
  fi
  GATEWAY_CONTAINER="${GATEWAY_CONTAINER:-ai-mcp-gateway}"
  GATEWAY_IMAGE="${GATEWAY_IMAGE:-ai-mcp-gateway}"
  GATEWAY_HOST_PORT="${GATEWAY_HOST_PORT:-8797}"
  GATEWAY_CONTAINER_PORT="${GATEWAY_CONTAINER_PORT:-8797}"
  GATEWAY_BIND_ADDRESS="${GATEWAY_BIND_ADDRESS:-127.0.0.1}"
  GATEWAY_NETWORK="${GATEWAY_NETWORK:-}"
  GATEWAY_HEALTH_PATH="${GATEWAY_HEALTH_PATH:-/api-gateway/admin/query_gateway_config_list}"
  GATEWAY_HEALTH_TIMEOUT="${GATEWAY_HEALTH_TIMEOUT:-120}"
  NGINX_CONTAINER="${NGINX_CONTAINER:-ai-mcp-gateway-nginx}"
  MYSQL_CONTAINER="${MYSQL_CONTAINER:-mysql}"
  MYSQL_DATABASE="${MYSQL_DATABASE:-ai_mcp_gateway_v2}"
  SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-dev}"
  SERVER_PORT="${SERVER_PORT:-${GATEWAY_CONTAINER_PORT}}"
  PREVIOUS_CONTAINER="${GATEWAY_CONTAINER}.previous"
  RUNTIME_ENV_FILE="${STATE_DIR}/runtime.env"
}

# The gateway container is the source of truth for runtime secrets on first use.
capture_runtime_env() {
  mkdir -p "${STATE_DIR}"
  chmod 700 "${STATE_DIR}"
  if [[ ! -f "${RUNTIME_ENV_FILE}" ]] && container_exists "${GATEWAY_CONTAINER}"; then
    docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' "${GATEWAY_CONTAINER}" >"${RUNTIME_ENV_FILE}"
    chmod 600 "${RUNTIME_ENV_FILE}"
  fi
}

# Read a single key without sourcing the captured environment into the shell.
env_value() {
  local key="$1" file="$2"
  [[ -f "${file}" ]] || return 0
  awk -F= -v key="${key}" '$1 == key {sub(/^[^=]*=/, ""); print; exit}' "${file}"
}

# Existing container metadata determines the network and database connection.
discover_runtime() {
  if [[ -z "${GATEWAY_NETWORK}" ]] && container_exists "${GATEWAY_CONTAINER}"; then
    GATEWAY_NETWORK="$(docker inspect -f '{{range $name, $value := .NetworkSettings.Networks}}{{println $name}}{{end}}' "${GATEWAY_CONTAINER}" | head -n1)"
  fi
  [[ -n "${GATEWAY_NETWORK}" ]] || fail "gateway network is unknown; set GATEWAY_NETWORK"
  DB_USER="${SPRING_DATASOURCE_USERNAME:-$(env_value SPRING_DATASOURCE_USERNAME "${RUNTIME_ENV_FILE}")}"
  DB_PASSWORD="${SPRING_DATASOURCE_PASSWORD:-$(env_value SPRING_DATASOURCE_PASSWORD "${RUNTIME_ENV_FILE}")}"
  [[ -n "${DB_USER}" ]] || DB_USER=root
  [[ -n "${DB_PASSWORD}" ]] || fail "database password is not available from the gateway runtime or operator env"
  local jdbc_url="${SPRING_DATASOURCE_URL:-$(env_value SPRING_DATASOURCE_URL "${RUNTIME_ENV_FILE}")}"
  if [[ -n "${jdbc_url}" ]]; then
    local parsed_db
    parsed_db="$(printf '%s' "${jdbc_url}" | sed -nE 's#^jdbc:mysql://[^/]+/([^?]+).*#\1#p')"
    [[ -n "${parsed_db}" ]] && MYSQL_DATABASE="${parsed_db}"
  fi
}

# MySQL credentials are passed through MYSQL_PWD and never appear in argv.
mysql_exec() {
  docker exec -e "MYSQL_PWD=${DB_PASSWORD}" "${MYSQL_CONTAINER}" \
    mysql --protocol=tcp -u"${DB_USER}" "${MYSQL_DATABASE}" "$@"
}

mysql_stream() {
  docker exec -i -e "MYSQL_PWD=${DB_PASSWORD}" "${MYSQL_CONTAINER}" \
    mysql --protocol=tcp -u"${DB_USER}" "${MYSQL_DATABASE}" "$@"
}

# A logical backup is the recovery point for every database update.
backup_database() {
  local run_dir="$1"
  local backup_file="${run_dir}/mysql-${MYSQL_DATABASE}.sql"
  log "creating MySQL backup"
  docker exec -e "MYSQL_PWD=${DB_PASSWORD}" "${MYSQL_CONTAINER}" \
    mysqldump --protocol=tcp -u"${DB_USER}" --single-transaction --routines --triggers --no-tablespaces "${MYSQL_DATABASE}" >"${backup_file}"
  chmod 600 "${backup_file}"
  [[ -s "${backup_file}" ]] || fail "MySQL backup is empty: ${backup_file}"
  printf '%s\n' "${backup_file}" >"${run_dir}/mysql-backup"
}

# Each migration is recorded only after its SQL stream succeeds.
apply_migrations() {
  [[ -d "${MIGRATIONS_DIR}" ]] || fail "migration directory not found: ${MIGRATIONS_DIR}"
  mysql_exec -e 'CREATE TABLE IF NOT EXISTS mcp_deployment_migration (migration_name VARCHAR(255) NOT NULL PRIMARY KEY, checksum CHAR(64) NOT NULL, applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;'
  local migration name checksum applied
  while IFS= read -r migration; do
    name="$(basename "${migration}")"
    checksum="$(sha256sum "${migration}" | awk '{print $1}')"
    applied="$(mysql_exec -N -B -e "SELECT COUNT(*) FROM mcp_deployment_migration WHERE migration_name='${name}';")"
    if [[ "${applied}" == "1" ]]; then
      log "migration already applied: ${name}"
      continue
    fi
    log "applying migration: ${name}"
    mysql_stream <"${migration}"
    mysql_exec -e "INSERT INTO mcp_deployment_migration (migration_name, checksum) VALUES ('${name}', '${checksum}');"
  done < <(find "${MIGRATIONS_DIR}" -maxdepth 1 -type f -name '*.sql' ! -name '._*' -print0 | sort -z | xargs -0 -r -n1 printf '%s\n')
}

# A sibling staging tree is copied into the existing bind-mounted inode, so
# Nginx keeps seeing the same host path while its contents are replaced.
clear_directory() {
  find "$1" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} +
}

stage_frontend() {
  [[ -f "${FRONTEND_SOURCE}/index.html" ]] || fail "current frontend index.html not found: ${FRONTEND_SOURCE}"
  local run_dir="$1" staging="${run_dir}/frontend-staging" backup="${run_dir}/frontend-backup"
  mkdir -p "${staging}" "${backup}"
  cp -a "${FRONTEND_SOURCE}/." "${staging}/"
  find "${staging}" -name '._*' -type f -delete
  if [[ -d "${NGINX_ROOT}" ]]; then
    cp -a "${NGINX_ROOT}/." "${backup}/"
  fi
  mkdir -p "${NGINX_ROOT}"
  clear_directory "${NGINX_ROOT}"
  cp -a "${staging}/." "${NGINX_ROOT}/"
  printf '%s\n' "${backup}" >"${run_dir}/frontend-backup-path"
}

# Restore the previous static root if any later verification fails.
restore_frontend() {
  local backup="$1"
  [[ -d "${backup}" ]] || return 0
  mkdir -p "${NGINX_ROOT}"
  clear_directory "${NGINX_ROOT}"
  cp -a "${backup}/." "${NGINX_ROOT}/"
}

# Build the current application from Maven or a prebuilt JAR supplied in the bundle.
build_image() {
  local build_context="${GATEWAY_BUILD_CONTEXT:-${SOURCE_ROOT}/ai-mcp-gateway-app}"
  local jar="${GATEWAY_JAR:-${build_context}/target/ai-mcp-gateway-app.jar}"
  if command -v mvn >/dev/null 2>&1 && [[ -f "${SOURCE_ROOT}/pom.xml" ]]; then
    (cd "${SOURCE_ROOT}" && mvn -pl ai-mcp-gateway-app -am package -Dmaven.test.skip=true)
  elif [[ -f "${jar}" ]]; then
    mkdir -p "${build_context}/target"
    if [[ "${jar}" != "${build_context}/target/ai-mcp-gateway-app.jar" ]]; then
      cp "${jar}" "${build_context}/target/ai-mcp-gateway-app.jar"
    fi
  else
    fail "Maven is unavailable and no prebuilt gateway JAR exists"
  fi
  GATEWAY_IMAGE_TAG="${GATEWAY_IMAGE}:$(date -u +%Y%m%d%H%M%S)"
  docker build --tag "${GATEWAY_IMAGE_TAG}" "${build_context}"
}

# Snapshot and stop only the old gateway container before binding its port.
preserve_gateway() {
  if container_exists "${PREVIOUS_CONTAINER}"; then docker rm -f "${PREVIOUS_CONTAINER}" >/dev/null; fi
  if container_exists "${GATEWAY_CONTAINER}"; then
    docker inspect -f '{{.Config.Image}}' "${GATEWAY_CONTAINER}" >"${CURRENT_RUN_DIR}/previous-image"
    docker rename "${GATEWAY_CONTAINER}" "${PREVIOUS_CONTAINER}"
    docker stop "${PREVIOUS_CONTAINER}" >/dev/null || true
  fi
}

start_gateway() {
  local env_args=()
  [[ -f "${RUNTIME_ENV_FILE}" ]] && env_args+=(--env-file "${RUNTIME_ENV_FILE}")
  [[ -f "${ENV_FILE}" ]] && env_args+=(--env-file "${ENV_FILE}")
  docker run --detach --name "${GATEWAY_CONTAINER}" \
    --restart unless-stopped \
    --publish "${GATEWAY_BIND_ADDRESS}:${GATEWAY_HOST_PORT}:${GATEWAY_CONTAINER_PORT}" \
    --network "${GATEWAY_NETWORK}" \
    "${env_args[@]}" \
    --env "SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE}" \
    --env "SERVER_PORT=${SERVER_PORT}" \
    --env "TZ=Asia/Shanghai" \
    "${GATEWAY_IMAGE_TAG}" >/dev/null
}

# The backend endpoint is probed directly before the public Nginx checks.
health_check() {
  local url="http://127.0.0.1:${GATEWAY_HOST_PORT}${GATEWAY_HEALTH_PATH}" started=$SECONDS
  while (( SECONDS - started < GATEWAY_HEALTH_TIMEOUT )); do
    if curl --silent --show-error --fail --max-time 4 "${url}" >/dev/null 2>&1; then return 0; fi
    sleep 2
  done
  return 1
}

reload_nginx() {
  container_exists "${NGINX_CONTAINER}" || fail "Nginx container not found: ${NGINX_CONTAINER}"
  docker exec "${NGINX_CONTAINER}" nginx -t >/dev/null
  docker exec "${NGINX_CONTAINER}" nginx -s reload >/dev/null
}

# Public verification confirms the new static tree and backend route are wired together.
verify_public() {
  curl --silent --show-error --fail --max-time 10 "${PUBLIC_BASE_URL}/" | grep -q 'MCP'
  curl --silent --show-error --fail --max-time 10 "${PUBLIC_BASE_URL}/api-gateway/console/auth/session" >/dev/null || true
  curl --silent --show-error --fail --max-time 10 "${PUBLIC_BASE_URL}${GATEWAY_HEALTH_PATH}" >/dev/null
}

verify_infrastructure() {
  local expected_mysql expected_redis expected_nginx
  expected_mysql="$(cat "${CURRENT_RUN_DIR}/mysql-container-id")"
  expected_redis="$(cat "${CURRENT_RUN_DIR}/redis-container-id")"
  expected_nginx="$(cat "${CURRENT_RUN_DIR}/nginx-container-id")"
  [[ "$(docker inspect -f '{{.Id}}' "${MYSQL_CONTAINER}")" == "${expected_mysql}" ]] || fail 'MySQL container identity changed'
  [[ "$(docker inspect -f '{{.Id}}' redis)" == "${expected_redis}" ]] || fail 'Redis container identity changed'
  [[ "$(docker inspect -f '{{.Id}}' "${NGINX_CONTAINER}")" == "${expected_nginx}" ]] || fail 'Nginx container identity changed'
}

restore_gateway() {
  docker rm -f "${GATEWAY_CONTAINER}" >/dev/null 2>&1 || true
  if container_exists "${PREVIOUS_CONTAINER}"; then
    docker rename "${PREVIOUS_CONTAINER}" "${GATEWAY_CONTAINER}"
    docker start "${GATEWAY_CONTAINER}" >/dev/null
  fi
}

update() {
  load_env
  require_tools
  mkdir -p "${STATE_DIR}"
  capture_runtime_env
  discover_runtime
  local run_id run_dir
  run_id="$(date -u +%Y%m%d%H%M%S)"
  run_dir="${STATE_DIR}/${run_id}"
  mkdir -p "${run_dir}"
  CURRENT_RUN_DIR="${run_dir}"
  printf '%s\n' "${run_id}" >"${LATEST_RUN_FILE}"
  docker inspect -f '{{.Id}}' "${MYSQL_CONTAINER}" >"${run_dir}/mysql-container-id"
  docker inspect -f '{{.Id}}' redis >"${run_dir}/redis-container-id"
  docker inspect -f '{{.Id}}' "${NGINX_CONTAINER}" >"${run_dir}/nginx-container-id"
  backup_database "${run_dir}"
  apply_migrations
  stage_frontend "${run_dir}"
  if ! build_image; then
    restore_frontend "${run_dir}/frontend-backup"
    fail "backend image build failed; database backup: ${run_dir}/mysql-${MYSQL_DATABASE}.sql"
  fi
  if ! preserve_gateway; then
    restore_frontend "${run_dir}/frontend-backup"
    fail "gateway snapshot failed; database backup: ${run_dir}/mysql-${MYSQL_DATABASE}.sql"
  fi
  if ! start_gateway || ! health_check; then
    log 'gateway health verification failed; restoring previous gateway and frontend'
    restore_gateway
    restore_frontend "${run_dir}/frontend-backup"
    fail "gateway update failed; database backup: ${run_dir}/mysql-${MYSQL_DATABASE}.sql"
  fi
  if ! reload_nginx || ! verify_public || ! verify_infrastructure; then
    log 'public verification failed; restoring previous gateway and frontend'
    restore_gateway
    restore_frontend "${run_dir}/frontend-backup"
    reload_nginx || true
    fail "full-stack verification failed; database backup: ${run_dir}/mysql-${MYSQL_DATABASE}.sql"
  fi
  printf '%s\n' "${GATEWAY_IMAGE_TAG}" >"${run_dir}/current-image"
  log "deployment complete: ${PUBLIC_BASE_URL}"
}

rollback() {
  load_env
  require_tools
  [[ -f "${LATEST_RUN_FILE}" ]] || fail 'no deployment snapshot is available'
  local run_id run_dir backup
  run_id="$(cat "${LATEST_RUN_FILE}")"
  run_dir="${STATE_DIR}/${run_id}"
  backup="${run_dir}/frontend-backup"
  restore_frontend "${backup}"
  container_exists "${PREVIOUS_CONTAINER}" || fail "no previous gateway container is available"
  restore_gateway
  reload_nginx
  health_check
  verify_public
  log "rollback complete: ${run_id}"
}

status() {
  load_env
  require_tools
  docker ps --filter "name=^/${GATEWAY_CONTAINER}$" --filter "name=^/${NGINX_CONTAINER}$" --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}'
  docker ps --filter 'name=^/mysql$' --filter 'name=^/redis$' --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}'
  [[ -f "${LATEST_RUN_FILE}" ]] && log "latest snapshot: $(cat "${LATEST_RUN_FILE}")"
}

require_tools() {
  command -v docker >/dev/null || fail 'docker is required'
  command -v curl >/dev/null || fail 'curl is required'
  command -v sha256sum >/dev/null || fail 'sha256sum is required'
}

case "${1:-update}" in
  update) update ;;
  status) status ;;
  rollback) rollback ;;
  *) fail "usage: $0 {update|status|rollback}" ;;
esac
