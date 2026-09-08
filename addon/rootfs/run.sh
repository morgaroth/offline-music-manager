#!/usr/bin/with-contenv bashio
# shellcheck shell=bash
set -e

# ---------------------------------------------------------------------------
# Read add-on options.
# ---------------------------------------------------------------------------
HTTP_PORT="$(bashio::config 'http_port')"
OUTPUT_SUBDIR="$(bashio::config 'output_subdir')"
DOWNLOADER="$(bashio::config 'downloader_exec')"

NFS_ENABLED="$(bashio::config 'nfs_enabled')"
NFS_EXPORT="$(bashio::config 'nfs_export')"
NFS_OPTIONS="$(bashio::config 'nfs_options')"

MOUNT_ROOT="/mnt/music"

# ---------------------------------------------------------------------------
# Mount the NFS music share (Navidrome target), if enabled.
# ---------------------------------------------------------------------------
if bashio::var.true "${NFS_ENABLED}"; then
    if ! bashio::config.has_value 'nfs_export'; then
        bashio::exit.nok "nfs_enabled is true but nfs_export is not set."
    fi

    bashio::log.info "Mounting NFS share ${NFS_EXPORT} at ${MOUNT_ROOT} ..."
    mkdir -p "${MOUNT_ROOT}"

    # rpcbind is needed by some NFS server/version combinations.
    rpcbind 2>/dev/null || true

    if mount -t nfs -o "${NFS_OPTIONS}" "${NFS_EXPORT}" "${MOUNT_ROOT}"; then
        bashio::log.info "NFS share mounted."
    else
        bashio::exit.nok "Failed to mount NFS share ${NFS_EXPORT}. Check export path, options, and that the server allows this client."
    fi

    OUTPUT_DIR="${MOUNT_ROOT}/${OUTPUT_SUBDIR}"
else
    # No NFS: write under the mapped HA /share (persisted, visible to other add-ons).
    OUTPUT_DIR="/share/${OUTPUT_SUBDIR}"
    bashio::log.info "NFS disabled; writing output to ${OUTPUT_DIR}"
fi

mkdir -p "${OUTPUT_DIR}"

# Cache dir stays on the add-on's local /data volume (fast, not on the NFS share).
CACHE_DIR="/data/cache"
mkdir -p "${CACHE_DIR}"

# ---------------------------------------------------------------------------
# Build the Postgres connection.
#   - If postgres_url is set, use it verbatim.
#   - Otherwise assemble it from host/port/database.
# ---------------------------------------------------------------------------
if bashio::config.has_value 'postgres_url'; then
    PG_URL="$(bashio::config 'postgres_url')"
else
    PG_HOST="$(bashio::config 'postgres_host')"
    PG_PORT="$(bashio::config 'postgres_port')"
    PG_DB="$(bashio::config 'postgres_database')"
    PG_URL="jdbc:postgresql://${PG_HOST}:${PG_PORT}/${PG_DB}"
fi

PG_USER="$(bashio::config 'postgres_user')"
PG_PASSWORD="$(bashio::config 'postgres_password')"

# ---------------------------------------------------------------------------
# Export env consumed by ServerConfig + application.conf.
# ---------------------------------------------------------------------------
export MUSIC_LIBRARY_HTTP_PORT="${HTTP_PORT}"
export MUSIC_LIBRARY_OUTPUT_DIR="${OUTPUT_DIR}"
export MUSIC_LIBRARY_CACHE_DIR="${CACHE_DIR}"
export MUSIC_LIBRARY_DOWNLOADER="${DOWNLOADER}"
export MUSIC_LIBRARY_POSTGRES_URL="${PG_URL}"
export MUSIC_LIBRARY_POSTGRES_USER="${PG_USER}"
export MUSIC_LIBRARY_POSTGRES_PASSWORD="${PG_PASSWORD}"

bashio::log.info "Starting Music Library on port ${HTTP_PORT}"
bashio::log.info "  output dir : ${OUTPUT_DIR}"
bashio::log.info "  postgres   : ${PG_URL} (user ${PG_USER})"

# ---------------------------------------------------------------------------
# Launch the staged app. exec so signals reach the JVM.
# ---------------------------------------------------------------------------
exec /opt/music-library/bin/musiclibraryhttp
