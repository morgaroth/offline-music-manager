# Tech Stack & Build

## Languages & core libraries

- **Scala 3.3.3** (Scala 3 syntax throughout: `enum`, `given`/`using`, braceless).
- **ZIO 2.1.6** — effect system. Uses ZIO 2 idioms only:
  - `ZIO.attempt` (not `ZIO.effect`), `ZIO.serviceWithZIO` (not `accessM`).
  - `ZLayer.fromZIO` / `ZLayer.scoped` / `ZLayer.fromFunction` — no `Has[_]`.
  - `ZIO.acquireRelease` + `Scope` instead of `ZManaged`.
  - `ZIO.foreachParDiscard(...).withParallelism(n)` for bounded parallelism.
- **circe 0.14.9** — parses yt-dlp JSON metadata (`derives Decoder`).
- **scopt 4.1.0** — CLI arg parsing (OParser).
- **PostgreSQL** via plain JDBC + **HikariCP 5.1.0** connection pool
  (`org.postgresql:postgresql:42.7.3`). No ORM — hand-written `PreparedStatement`s.
- **Flyway 10.15.0** — schema migrations, run on connection-pool creation.
  Needs `flyway-database-postgresql` (Runtime scope) for PG support in Flyway 10+.
- **ScalaFX 22.0.0-R33 / JavaFX 22** — desktop GUI (second-impl module).
- Logging: logback + scala-logging.

## Module layout (multi-module sbt build)

- **domain** — pure model + business logic. `Track`, `TrackStatus` (enum),
  `ZioTracksStorageService` (storage trait), `ZIOFetcher` (download/convert/tag
  pipeline), `MetaDataFetcher`, `Args`/`Configuration`. No DB dependency.
- **main** — PostgreSQL implementation + entry point.
  `TracksStoragePostgres` (JDBC impl of `ZioTracksStorageService`),
  `DatabaseConfig` + `DataSourceLive` (Hikari + Flyway layer), `Boot` (ZIOAppDefault).
  Resources: `application.conf`, `logback.xml`, `db/migration/V1__create_tracks_table.sql`.
- **second-impl** — ScalaFX GUI. Depends on `main`.
  `MusicLibraryApp` (JFXApp3 + tabs), `ManageTab`, `RunTab`, `BrowseTab`,
  `TrackDetailsPane`, `GuiBackend` (ZIO service over storage), `FxBridge`.

## Common commands

```bash
sbt compile                 # compile all modules
sbt "main/run fetch"        # run batch downloader (CLI)
sbt "SecondImpl/run"        # launch the ScalaFX GUI
sbt "main/deploy"           # build .deb and install locally (deploy.sh)
sbt "SecondImpl/compile"    # compile just the GUI module
```

## Verify after changes

Always run `sbt compile` before declaring done. The whole build must be green
across domain, main, and SecondImpl.

## Known Scala-3-specific gotchas

- `mongo-scala-driver` codec **macros are Scala 2 only** — that's why storage was
  moved to hand-written JDBC. Do not reintroduce macro-based codecs.
- `zio.System` / `zio.Runtime` shadow `java.lang.System` / `java.lang.Runtime`;
  fully-qualify the `java.lang.` ones inside ZIO-importing files.
- ScalaFX enums need scalafx types (e.g. `scalafx.geometry.Orientation.Vertical`,
  not the javafx constant) in property assignments.
- A `val` that references itself in its body (e.g. a Button whose handler disables
  itself) needs an explicit type annotation to avoid a cyclic-reference error.
