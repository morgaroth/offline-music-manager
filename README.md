# offline-music-manager

A personal offline music library manager. Downloads audio from YouTube, processes it
(volume normalization, trimming, fade-out), tags it with ID3 metadata, and organizes
it into playlists. Tracks are stored in PostgreSQL.

It runs as a single HTTP service that serves a web UI and a JSON API, with a
yt-dlp/ffmpeg executor behind it. It ships two ways:

- a plain **Docker image** (bind a port, open the UI from `localhost`), and
- a **Home Assistant add-on** (the same container, wrapped with HA config + an NFS
  mount for the Navidrome music volume).

An **OpenClaw plugin** exposes the API as agent tools, so you can manage songs by
sharing a link with an OpenClaw agent.

Built with **Scala 3.3.3**, **ZIO 2.1.6**, **zio-http 3.11.4**, and
**PostgreSQL (JDBC + HikariCP + Flyway)**.

## Modules

- **domain** — shared library: model, storage interface, and the yt-dlp/ffmpeg
  fetch pipeline. No DB/HTTP dependency.
- **http** — the application: Postgres storage, HTTP server, web UI, JSON API,
  and the `serve`/`fetch` entrypoint dispatcher.

## Prerequisites

### Runtime tools (must be on PATH when running outside the container)

- **JDK 21+**
- **PostgreSQL** server
- **yt-dlp** + **Deno** (YouTube needs a JS runtime for signature solving)
- **ffmpeg** — audio conversion
- **mid3v2** — ID3 tagging (`pip install mutagen`)

The Docker image / add-on installs all of these for you.

### Database setup

```bash
createdb music_library
```

Flyway creates the schema automatically on first connect (it will NOT create the
database). Configure the connection via `http/src/main/resources/application.conf`
or environment variables:

```bash
export MUSIC_LIBRARY_POSTGRES_URL="jdbc:postgresql://localhost:5432/music_library"
export MUSIC_LIBRARY_POSTGRES_USER="postgres"
export MUSIC_LIBRARY_POSTGRES_PASSWORD=""
```

## Build & run

```bash
sbt "http/compile"       # compile everything
sbt "http/run"           # start the HTTP server + UI (serve mode, default)
sbt "http/run fetch"     # CLI batch download of all ready-to-fetch tracks
sbt "http/stage"         # produce a runnable app under http/target/universal/stage
```

The UI and API listen on `:8080` by default (`MUSIC_LIBRARY_HTTP_PORT` to change).
Open `http://localhost:8080`.

## Docker / Home Assistant add-on

The add-on lives in `addon/`. Because it ships a pre-staged JVM app, run the
staging step once before building the image:

```bash
./addon/stage-addon.sh
```

See `addon/DOCS.md` for install and configuration (external Postgres, NFS output
for Navidrome, port).

## OpenClaw

The agent entrypoint lives in `openclaw-plugin/`. Point its `baseUrl` at the
running server. See `openclaw-plugin/README.md`.

## Syncing to Navidrome

The library is served by Navidrome over an NFS mount. The add-on mounts the NFS
export and writes finished tracks there directly. See **SYNC.md** for the
HAOS-level automount notes.

## Continuing this work in Kiro

Project context for the Kiro IDE lives in `.kiro/steering/`:
- `product.md` — what the app does and its external tool deps
- `tech.md` — stack, build commands, Scala-3/ZIO-2 gotchas
- `structure.md` — file layout and architecture conventions

Read those first when resuming on a new workstation.
