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
│       │   ├── ServerConfig.scala    # env-driven port/output/cache/downloader
│       │   └── JobRegistry.scala     # async fetch jobs (forkDaemon + Ref status)
│       └── storage/
│           ├── DatabaseConfig.scala          # config + DataSourceLive (Hikari + Flyway)
│           └── TracksStoragePostgres.scala   # JDBC impl of ZioTracksStorageService
│
├── addon/                        # Home Assistant add-on (Docker)
│   ├── config.yaml               # options/schema, ports, map, privileges
│   ├── build.yaml                # base image per arch (HA Ubuntu base)
│   ├── Dockerfile                # JRE + ffmpeg + yt-dlp + nfs + mutagen; COPY rootfs
│   ├── DOCS.md                   # add-on install/config docs
│   ├── stage-addon.sh            # sbt http/stage → rootfs/opt/music-library
│   └── rootfs/run.sh             # bashio: mount NFS, build PG url, exec app
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
- **Config precedence**: env vars override `application.conf`
  (`MUSIC_LIBRARY_POSTGRES_URL/USER/PASSWORD`) and drive `ServerConfig`
  (`MUSIC_LIBRARY_HTTP_PORT/OUTPUT_DIR/CACHE_DIR/DOWNLOADER`).

## Database

- Table `tracks`, UUID PK, `TEXT[]` playlists column, `TIMESTAMPTZ` dates.
- Create the DB manually first: `createdb music_library`. Flyway creates the schema
  on first connection but does NOT create the database itself.

## Deferred / future work

- Live executor logs streamed into the UI (SSE): three log panes for the bulk
  fetch (parallel workers kept separate) + a single log for single-track "sync
  this one now". Currently logs go to the container console only.
