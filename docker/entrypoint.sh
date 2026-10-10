#!/usr/bin/env sh
# HA-agnostic entrypoint.
#
# The app reads all its own config from /data/options.json (HA add-on) or
# MEDIA_LIBRARY_* env (standalone), including the cache dir (which it defaults to
# /data/cache when /data exists). Nothing to set up here; just exec.
set -eu

echo "[entrypoint] starting app: $*"
exec "$@"
