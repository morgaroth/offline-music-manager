# Tech Stack & Build

## Languages & core libraries

- **Scala 3.3.3** (Scala 3 syntax throughout: `enum`, `given`/`using`, braceless).
- **ZIO 2.1.6** — effect system. Uses ZIO 2 idioms only:
  - `ZIO.attempt` (not `ZIO.effect`), `ZIO.serviceWithZIO` (not `accessM`).
  - `ZLayer.fromZIO` / `ZLayer.scoped` / `ZLayer.fromFunction` — no `Has[_]`.
  - `ZIO.acquireRelease` + `Scope` instead of `ZManaged`.
  - `ZIO.foreachParDiscard(...).withParallelism(n)` for bounded parallelism.
- **zio-http 3.11.4** — HTTP server, web UI, and JSON API (ZIO 2 line).
- **circe 0.14.9** — parses yt-dlp JSON metadata; hand-written codecs for the
  HTTP layer (`http/JsonCodecs.scala`).
- **scopt 4.1.0** — CLI arg parsing (OParser) for the `fetch` batch mode.
- **PostgreSQL** via plain JDBC + **HikariCP 5.1.0** connection pool
  (`org.postgresql:postgresql:42.7.3`). No ORM — hand-written `PreparedStatement`s.
- **Flyway 10.15.0** — schema migrations, run on connection-pool creation.
  Needs `flyway-database-postgresql` (Runtime scope) for PG support in Flyway 10+.
- Logging: logback + scala-logging (console/stdout appender).

## Module layout (2-module sbt build)

- **domain** — the shared library. Dependency-light (no JDBC, no HTTP).
  `Track`, `TrackStatus` (enum), `ZioTracksStorageService` (storage trait),
  `ZIOFetcher` (download/convert/tag pipeline), `MetaDataFetcher`,
  `Args`/`Configuration`.
- **http** — the single application. Depends on `domain`.
  - Postgres storage: `TracksStoragePostgres` (JDBC impl), `DatabaseConfig` +
    `DataSourceLive` (Hikari + Flyway layer).
  - Server: `HttpApp` (entrypoint dispatcher), `MusicRoutes` (routes),
    `WebUi` (inline HTML UI), `JsonCodecs`, `ServerConfig` (env-driven),
    `JobRegistry` (async fetch jobs).
  - Resources: `application.conf`, `logback.xml`,
    `db/migration/V1__create_tracks_table.sql`.

The ScalaFX GUI (`second-impl`) and the CLI `main`/Debian packaging were removed;
the app is now HTTP-only, shipped as a Docker image / Home Assistant add-on.

## Deliverables outside the sbt build

- **addon/** — Home Assistant add-on (Docker). `config.yaml`, `build.yaml`,
  `Dockerfile` (HA Ubuntu base + JRE/ffmpeg/yt-dlp/nfs/mutagen), `rootfs/run.sh`
  (bashio), `stage-addon.sh` (pre-stages the app into `rootfs/opt/music-library`).
- **openclaw-plugin/** — TypeScript OpenClaw plugin registering agent tools that
  call the HTTP API.

## Entry point

Single dispatcher: `io.morgaroth.media.library.http.HttpApp`.
- no args / `serve` → HTTP server + UI + API (default; used by Docker/add-on).
- `fetch [opts]` → foreground batch download of all ready-to-fetch tracks.

## Common commands

```bash
sbt "http/compile"          # compile the app (and domain transitively)
sbt "http/run"              # run the server locally (serve mode)
sbt "http/run fetch"        # run the batch downloader
sbt "http/stage"            # produce the runnable app under http/target/universal/stage
./addon/stage-addon.sh      # stage + copy app into the add-on build context
```

## Verify after changes

Always run `sbt "http/compile"` before declaring done. For container changes,
re-run `./addon/stage-addon.sh` then `podman build` (or `docker build`) the
add-on image.

## Deferred / future work

- **Live executor logs in the UI**: stream fetch progress to the browser (SSE),
  with three separate log panes for the bulk fetch (so parallel workers don't
  interleave) and a single log for a single-track "sync this one now" action.
  Not yet implemented — logs currently go to the container console only.

## Known Scala-3-specific gotchas

- Storage is hand-written JDBC by design (Scala-2-only codec macros were the
  reason the old Mongo driver was dropped). Do not reintroduce macro-based codecs.
- `zio.System` / `zio.Runtime` shadow `java.lang.System` / `java.lang.Runtime`;
  fully-qualify the `java.lang.` ones (or avoid `import zio.*`) inside files that
  need them (bit us in `ServerConfig`).
- `uuid` is provided by both `zio.http.*` and `zio.http.codec.PathCodec.*`;
  import only `zio.http.*` to avoid an ambiguous reference.
- A `val` that references itself in its body needs an explicit type annotation to
  avoid a cyclic-reference error.
