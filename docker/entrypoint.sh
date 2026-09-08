#!/usr/bin/env sh
# HA-agnostic entrypoint.
#
# Responsibilities kept in shell (not the JVM):
#   - optionally mount the NFS music share (needs container mount privileges)
#   - ensure output/cache dirs exist
# then exec the app, which reads its own config from /data/options.json or env.
#
# Config source, same precedence the app uses:
#   /data/options.json (HA add-on)  ->  MUSIC_LIBRARY_* env  ->  default
set -eu

OPTIONS_FILE="/data/options.json"

# opt <json_key> <env_var> <default>
opt() {
  _key="$1"; _env="$2"; _def="${3:-}"
  # env override / standalone
  _envval="$(eval "printf '%s' \"\${$_env:-}\"")"
  if [ -n "$_envval" ]; then
    printf '%s' "$_envval"; return 0
  fi
  # add-on options.json
  if [ -f "$OPTIONS_FILE" ]; then
    _v="$(jq -r --arg k "$_key" '.[$k] // empty' "$OPTIONS_FILE" 2>/dev/null || true)"
    if [ -n "$_v" ] && [ "$_v" != "null" ]; then
      printf '%s' "$_v"; return 0
    fi
  fi
  printf '%s' "$_def"
}

NFS_ENABLED="$(opt nfs_enabled MUSIC_LIBRARY_NFS_ENABLED false)"
NFS_EXPORT="$(opt nfs_export MUSIC_LIBRARY_NFS_EXPORT '')"
NFS_OPTIONS="$(opt nfs_options MUSIC_LIBRARY_NFS_OPTIONS 'rw,vers=4,proto=tcp,hard,timeo=600')"
OUTPUT_SUBDIR="$(opt output_subdir MUSIC_LIBRARY_OUTPUT_SUBDIR offline-music-manager)"
MOUNT_ROOT="/mnt/music"

if [ "$NFS_ENABLED" = "true" ] || [ "$NFS_ENABLED" = "1" ]; then
  if [ -z "$NFS_EXPORT" ]; then
    echo "[entrypoint] nfs enabled but no export configured" >&2
    exit 1
  fi
  echo "[entrypoint] mounting NFS $NFS_EXPORT -> $MOUNT_ROOT"
  mkdir -p "$MOUNT_ROOT"
  rpcbind 2>/dev/null || true
  if ! mount -t nfs -o "$NFS_OPTIONS" "$NFS_EXPORT" "$MOUNT_ROOT"; then
    echo "[entrypoint] NFS mount failed" >&2
    exit 1
  fi
  # The app derives <mount>/<subdir> for output when nfs_enabled; make sure it exists.
  mkdir -p "$MOUNT_ROOT/$OUTPUT_SUBDIR"
fi

# Cache dir on the persistent /data volume (or a local default when not present).
if [ -d /data ]; then
  export MUSIC_LIBRARY_CACHE_DIR="${MUSIC_LIBRARY_CACHE_DIR:-/data/cache}"
  mkdir -p "$MUSIC_LIBRARY_CACHE_DIR"
fi

echo "[entrypoint] starting app: $*"
exec "$@"
