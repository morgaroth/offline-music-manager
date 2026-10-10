#!/usr/bin/env sh
# HA-agnostic entrypoint.
#
# The app reads its own config from /data/options.json (HA add-on) or
# MUSIC_LIBRARY_* env (standalone). The only thing kept in shell is pinning the
# cache dir onto the persistent /data volume when present.
set -eu

# Cache dir on the persistent /data volume (or the app's own default otherwise).
if [ -d /data ]; then
  export MUSIC_LIBRARY_CACHE_DIR="${MUSIC_LIBRARY_CACHE_DIR:-/data/cache}"
  mkdir -p "$MUSIC_LIBRARY_CACHE_DIR"
fi

echo "[entrypoint] starting app: $*"
exec "$@"
