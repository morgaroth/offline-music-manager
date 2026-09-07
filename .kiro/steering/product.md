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
5. **GUI** — a ScalaFX desktop app with three tabs:
   - **Manage**: edit a single track's details, search/select, pull one song.
   - **Run**: batch-download with 3 concurrent workers, per-worker logs + progress.
   - **Browse**: full-window searchable table of all tracks.
6. **CLI** — `Boot` entry point supports `fetch`/`boot`/`work` (batch downloader) and
   `gui` (which just points to the second-impl GUI).

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
