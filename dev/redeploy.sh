#!/usr/bin/env bash
# Rebuild the image and (re)start the long-running test container against the
# testing Postgres. Idempotent: safe to run on every change.
#
# Usage: ./dev/redeploy.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

IMAGE="omm:test"
CONTAINER="omm"
PORT="57381"

# Testing DB (override via env if needed).
PG_URL="${MEDIA_LIBRARY_POSTGRES_URL:-jdbc:postgresql://192.168.0.20:5432/music-library-tests}"
PG_USER="${MEDIA_LIBRARY_POSTGRES_USER:-kiro}"
PG_PASSWORD="${MEDIA_LIBRARY_POSTGRES_PASSWORD:-choose-a-strong-password}"

echo "==> building ${IMAGE}"
podman build -t "${IMAGE}" "${REPO_ROOT}"

echo "==> replacing container ${CONTAINER}"
podman rm -f "${CONTAINER}" >/dev/null 2>&1 || true

# Output to a named volume so it persists across container recreations.
podman run -d --name "${CONTAINER}" \
  -p "${PORT}:${PORT}" \
  -e MEDIA_LIBRARY_HTTP_PORT="${PORT}" \
  -e MEDIA_LIBRARY_MUSIC_OUTPUT_PATH=/downloads \
  -e MEDIA_LIBRARY_VIDEO_OUTPUT_PATH=/videos \
  -e MEDIA_LIBRARY_CACHE_PATH=/cache \
  -v omm-videos:/videos \
  -v omm-cache:/cache \
  -e MEDIA_LIBRARY_POSTGRES_URL="${PG_URL}" \
  -e MEDIA_LIBRARY_POSTGRES_USER="${PG_USER}" \
  -e MEDIA_LIBRARY_POSTGRES_PASSWORD="${PG_PASSWORD}" \
  -v $PWD/omm-downloads:/downloads \
  "${IMAGE}" >/dev/null

echo "==> waiting for startup..."
for i in $(seq 1 30); do
  if curl -fsS "http://localhost:${PORT}/health" >/dev/null 2>&1; then
    echo "==> up: http://localhost:${PORT}  (health OK)"
    exit 0
  fi
  sleep 1
done

echo "!! health check did not pass in time; recent logs:" >&2
podman logs --tail 30 "${CONTAINER}" >&2 || true
exit 1
