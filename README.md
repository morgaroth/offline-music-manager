# offline-music-manager

A personal offline music library manager. Downloads audio from YouTube, processes it
(volume normalization, trimming, fade-out), tags it with ID3 metadata, and organizes
it into playlists. Tracks are stored in PostgreSQL. Ships a ScalaFX desktop GUI and a
CLI batch downloader.

Built with **Scala 3.3.3**, **ZIO 2.1.6**, **PostgreSQL (JDBC + HikariCP + Flyway)**,
and **ScalaFX 22 / JavaFX 22**.

## Prerequisites

### Runtime tools (must be on PATH)

- **JDK 17+**
- **sbt 1.10.1** (the build declares it; a bootstrapper will fetch it)
- **PostgreSQL** server
- **yt-dlp** + **Deno** (YouTube now needs a JS runtime for signature solving):
  ```bash
  curl -fsSL https://deno.land/install.sh | sh     # Deno (recommended by yt-dlp)
  pip install -U "yt-dlp[default]"                 # yt-dlp + EJS challenge scripts
  ```
- **ffmpeg** — audio conversion
- **mid3v2** — ID3 tagging (`pip install mutagen`)
- A **Chrome** profile logged into YouTube (yt-dlp uses `--cookies-from-browser chrome`)

### Database setup

```bash
createdb music_library
```

Flyway creates the schema automatically on first connect (it will NOT create the
database). Configure the connection via `main/src/main/resources/application.conf`
or environment variables:

```bash
export MUSIC_LIBRARY_POSTGRES_URL="jdbc:postgresql://localhost:5432/music_library"
export MUSIC_LIBRARY_POSTGRES_USER="postgres"
export MUSIC_LIBRARY_POSTGRES_PASSWORD=""
```

## Build & run

```bash
sbt compile              # build everything
sbt "SecondImpl/run"     # launch the GUI (Manage / Run / Browse tabs)
sbt "main/run fetch"     # CLI batch download of all ready-to-fetch tracks
sbt "main/deploy"        # build a .deb and install locally
```

## GUI tabs

- **Manage** — edit one track (URL, artist, title, album, start/end, fade, playlists),
  search + double-click to load, and **Pobierz** to download that single track.
- **Run** — batch download with 3 concurrent workers, each with its own live log and
  progress indicator, plus an overall progress bar.
- **Browse** — full-window searchable table of every track; double-click jumps to Manage.

## Migrating from an old MongoDB instance

Storage was migrated from MongoDB to PostgreSQL. A one-time migration script is
provided:

```bash
uv run --with pymongo --with psycopg2-binary migrate_mongo_to_postgres.py
```

(Env: `MONGO_URI`, `MONGO_COLLECTION`, `POSTGRES_URL`.) Safe to re-run — it uses
`ON CONFLICT (id) DO NOTHING`.

## Syncing to Navidrome

The library is served by Navidrome over an NFS mount. See **SYNC.md** for the systemd
automount setup. In short: point the download destination (or rsync target) at the
NFS-mounted music folder and Navidrome picks up new files on its next scan.

## Continuing this work in Kiro

Project context for the Kiro IDE lives in `.kiro/steering/`:
- `product.md` — what the app does and its external tool deps
- `tech.md` — stack, build commands, Scala-3/ZIO-2 gotchas
- `structure.md` — file layout and architecture conventions

Read those first when resuming on a new workstation.
