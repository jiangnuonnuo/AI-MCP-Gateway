#!/usr/bin/env bash
set -Eeuo pipefail

# Keep the original operator entrypoint while routing all operations through
# the current full-stack deployment contract.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec "${SCRIPT_DIR}/deploy-current-stack.sh" "${1:-update}"
