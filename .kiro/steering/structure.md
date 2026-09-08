# Project Structure

```
offline-music-manager/
├── build.sbt                     # 2 modules: domain, http
├── project/
│   ├── build.properties          # sbt version
│   └── plugins.sbt               # sbt-native-packager 1.10.0
├── repository.yaml               # marks repo as an HA add-on repository
├── SYNC.md                       # Navidrome-over-NFS automount setup guide
│
├── domain/src/main/scala/io/morgaroth/media/library/   # shared library
│   ├── package.scala             # ErrorOr alias, md5HashString
│   ├── arguments.scala           # Configuration + Args (scopt OParser, for `fetch`)
│   ├── common/package.scala      # given Ordering[ZonedDateTime]
│   ├── storage/
│   │   ├── TrackStatus.scala     # enum Draft/Final/Deleted
│   │   ├── TrackDB.scala         # Track case class + legacy TracksStorage[F] trait
│   │   └── ZioTracksStorageService.scala  # ZIO 2 storage trait + accessor object
│   └── jobs/
│       ├── YoutubeDLMeta.scala   # circe-derived yt-dlp metadata + URLFetchError
│       ├── MetaDataFetcher.scala # yt-dlp --print-json wrapper
│       └── ZIOFetcher.scala      # download → convert → trim → tag → playlists pipeline
│
├── http/src/main/               # the single application (depends on domain)
│   ├── resources/
│   │   ├── application.conf      # music-library.postgres.{url,user,password} (+ env overrides)
│   │   ├── logback.xml           # console appender; storage pkg at DEBUG
│   │   └── db/migration/V1__create_tracks_table.sql
│   └── scala/io/morgaroth/media/library/
│       ├── http/
│       │   ├── HttpApp.scala         # entrypoint dispatcher: serve (default) / fetch
│       │   ├── MusicRoutes.scala     # UI + JSON API routes
│       │   ├── WebUi.scala           # self-contained inline HTML/CSS/JS UI
│       │   ├── JsonCodecs.scala      # circe codecs for Track/TrackStatus
│       │   ├── OptionsSource.scala   # /data/options.json | MUSIC_LIBRARY_* env | default
│       │   ├── ServerConfig.scala    # port/output/cache resolved via OptionsSource
│       │   └── JobRegistry.scala     # async fetch jobs (forkDaemon + Ref status)
│       └── storage/
│           ├── DatabaseConfig.scala          # config + DataSourceLive (Hikari + Flyway)
│           └── TracksStoragePostgres.scala   # JDBC impl of ZioTracksStorageService
│
├── Dockerfile                    # root, HA-agnostic image (multi-stage sbt → temurin JRE)
├── docker/entrypoint.sh          # sh+jq: mount NFS if configured, ensure dirs, exec app
├── .gitlab-ci.yml                # verify:compile + per-arch buildx push
├── .dockerignore
│
├── addon/                        # Home Assistant add-on (pull model)
│   ├── config.yaml               # image:/version: pull, native options schema, ports, map, privileges
│   └── DOCS.md                   # add-on install/config docs
│
└── openclaw-plugin/              # OpenClaw agent entrypoint (TypeScript)
    ├── openclaw.plugin.json      # manifest (contracts.tools)
    ├── package.json
    └── src/index.ts              # registerTool(...) -> HTTP calls
```

## Architecture conventions

- **Layer wiring**: `DatabaseConfig` → `DataSourceLive.layer` →
  `TracksStoragePostgres.layer` → `ZIOFetcher.live` / `JobRegistry.live` /
  `MusicRoutes.live`. See `HttpApp`.
- **Entry point**: one dispatcher, `HttpApp`. Args select `serve` (default) or
  `fetch`. Both log to stdout so `docker logs` / the HA add-on log page show them.
- **Fetches are async over HTTP**: `POST /tracks/{id}/fetch` forks a daemon fiber
  and returns a job id; poll `GET /jobs/{id}`. Wrap blocking fetch/ffmpeg work in
  `ZIO.blocking`.
- **Storage**: every method returns `Task[...]`. Hand-written JDBC; every query is
  logged via a `logQuery` helper.
- **Config precedence** (`OptionsSource`): `/data/options.json` (HA add-on) →
  `MUSIC_LIBRARY_*` env (standalone) → default / `application.conf`. `HttpApp`
  builds `DatabaseConfig` (full `postgres_url` or host/port/db parts) and
  `ServerConfig` (`http_port`, `output_subdir` under `/mnt/music` when
  `nfs_enabled`, else `MUSIC_LIBRARY_OUTPUT_DIR`). The bundled downloader is
  `yt-dlp` (not a user option; `MUSIC_LIBRARY_DOWNLOADER` env is an escape hatch).

## Database

- Table `tracks`, UUID PK, `TEXT[]` playlists column, `TIMESTAMPTZ` dates.
- Create the DB manually first: `createdb music_library`. Flyway creates the schema
  on first connection but does NOT create the database itself.

## Deferred / future work

- Live executor logs streamed into the UI (SSE): three log panes for the bulk
  fetch (parallel workers kept separate) + a single log for single-track "sync
  this one now". Currently logs go to the container console only.
