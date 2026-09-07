# Project Structure

```
offline-music-manager/
├── build.sbt                     # 3 modules: domain, main, SecondImpl
├── project/
│   ├── build.properties          # sbt 1.10.1
│   └── plugins.sbt               # sbt-native-packager 1.10.0
├── SYNC.md                       # Navidrome-over-NFS automount setup guide
├── migrate_mongo_to_postgres.py  # one-time Mongo → Postgres migration (uv run)
│
├── domain/src/main/scala/io/morgaroth/media/library/
│   ├── package.scala             # ErrorOr alias, md5HashString
│   ├── arguments.scala           # Configuration + Args (scopt OParser)
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
├── main/src/main/
│   ├── resources/
│   │   ├── application.conf      # music-library.postgres.{url,user,password} (+ env overrides)
│   │   ├── logback.xml           # storage pkg at DEBUG for SQL logging
│   │   └── db/migration/V1__create_tracks_table.sql
│   └── scala/io/morgaroth/media/library/
│       ├── jobs/Boot.scala       # ZIOAppDefault entry point (fetch / gui)
│       └── storage/
│           ├── DatabaseConfig.scala          # config + DataSourceLive (Hikari + Flyway)
│           └── TracksStoragePostgres.scala   # JDBC impl + TracksStorageService layer
│
└── second-impl/src/main/scala/io/morgaroth/media/library/gui/
    ├── MusicLibraryApp.scala     # JFXApp3, builds ZIO runtime from layer, 3 tabs
    ├── FxBridge.scala            # runs Task off-thread, delivers result on FX thread
    ├── GuiBackend.scala          # ZIO service wrapping ZioTracksStorageService
    ├── ManageTab.scala           # SplitPane: TrackDetailsPane + compact search table
    ├── TrackDetailsPane.scala    # single-track editor + "Pobierz" (pull) button
    ├── RunTab.scala              # 3 concurrent workers, per-worker logs + progress
    └── BrowseTab.scala           # full-window search table, dbl-click → Manage tab
```

## Architecture conventions

- **Layer wiring**: `DatabaseConfig` → `DataSourceLive.layer` → `TracksStoragePostgres.layer`
  → (`GuiBackend.live` for GUI, or `ZIOFetcher.live` for CLI).
- **GUI ↔ ZIO bridge**: UI runs on the JavaFX thread. To run an effect, call
  `FxBridge.run(runtime)(task)(onSuccess, onError)`. It forks the effect onto ZIO
  fibers and delivers the result back via `Platform.runLater`. **Wrap blocking
  fetch/ffmpeg work in `ZIO.blocking`** — the GUI runtime's default executor is
  limited and blocking process calls will starve it otherwise (this was a real bug).
- **Storage**: every method returns `Task[...]`. Hand-written JDBC; every query is
  logged via a `logQuery` helper (currently also `println` for visibility).
- **Naming**: UI labels are in Polish (Zapisz, Pobierz, Szukaj, Twórca, Tytuł, etc.).

## Database

- Table `tracks`, UUID PK, `TEXT[]` playlists column, `TIMESTAMPTZ` dates.
- Create the DB manually first: `createdb music_library`. Flyway creates the schema
  on first connection but does NOT create the database itself.
- Config via env: `MUSIC_LIBRARY_POSTGRES_URL/USER/PASSWORD`.
