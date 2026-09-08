#!/usr/bin/env bash
# Build the HTTP app and copy the staged output into the add-on build context.
#
# Run this from the repository root (or anywhere; it resolves paths itself)
# before building/pushing the add-on image. The Supervisor build context is the
# `addon/` directory, so the runnable app must live under addon/rootfs.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
STAGE_SRC="${REPO_ROOT}/http/target/universal/stage"
STAGE_DST="${SCRIPT_DIR}/rootfs/opt/music-library"

echo "==> sbt http/stage"
( cd "${REPO_ROOT}" && sbt -batch "http/stage" )

echo "==> copying staged app -> ${STAGE_DST}"
rm -rf "${STAGE_DST}"
mkdir -p "${STAGE_DST}"
cp -R "${STAGE_SRC}/." "${STAGE_DST}/"

echo "==> done. Staged app is in addon/rootfs/opt/music-library"
