# Product Overview

**offline-music-manager** is a personal offline music library manager. It downloads
audio tracks from YouTube, processes them (volume normalization, trimming, fade-out),
tags them with ID3 metadata, and organizes them into playlists on the local filesystem.

## Core capabilities

1. **Track database** — track definitions (URL, title, artist, album, start/end
   timestamps, fade-out, volume adjustment, playlists, status) stored in PostgreSQL.
   Statuses: `draft`, `final`, `deleted`.
2. **Downloading** — pulls audio from YouTube via `yt-dlp`, with retry logic.
3. **Audio processing** — `ffmpeg` handles volume normalization (`volumedetect`),
   trimming to start/end, and fade-out effects.
4. **Tagging** — `mid3v2` (python-mutagen) writes ID3 tags and a UFID hash used for
   change detection (avoids re-processing unchanged tracks).
5. **Web UI + JSON API** — a single HTTP service (zio-http) serves a self-contained
   web UI for searching, adding, editing, and fetching tracks, and a JSON API used
   by automation. Fetches run asynchronously as pollable jobs.
6. **Entry point** — one dispatcher: `serve` (default; HTTP server + UI + API) and
   `fetch` (foreground batch downloader). Both log to stdout.
7. **OpenClaw plugin** — exposes the API as agent tools so tracks can be managed by
   sharing a link with an OpenClaw agent.

## Delivery

- Runs standalone as a **Docker image** (bind a port, open the UI from localhost).
- Runs as a **Home Assistant add-on** (same container, HA supplies config + an NFS
  mount for the Navidrome music volume).

## Deferred / future work

- Live executor logs in the UI: three separate log panes for the bulk fetch (so
  parallel workers don't interleave) plus a single log for a single-track
  "sync this one now" action. Not yet built — logs currently go to the console.

## External tool dependencies (must be on PATH at runtime)

- `yt-dlp` — YouTube downloader. YouTube now requires a JS runtime for signature
  solving; **Deno must be installed** (`curl -fsSL https://deno.land/install.sh | sh`)
  and `pip install -U "yt-dlp[default]"` for the EJS scripts.
- `ffmpeg` — audio conversion / processing.
- `mid3v2` — ID3 tagging (from python-mutagen).
- YouTube auth: yt-dlp is invoked with `--cookies-from-browser chrome`.

## Notes

- Playlists are currently hardcoded: `electronic`, `christmas`, `all`, `zbysio`.
- Music library syncs to Navidrome over NFS — see `SYNC.md` in the repo root.
- `pull-bajki.sh` (kept under `~/Wideo/bajki`, not in this repo) is an unrelated
  personal helper for downloading webOS-compatible MP4 videos.
