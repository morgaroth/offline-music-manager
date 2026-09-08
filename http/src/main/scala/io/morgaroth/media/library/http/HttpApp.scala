package io.morgaroth.media.library.http

import com.typesafe.config.ConfigFactory
import io.morgaroth.media.library.jobs.ZIOFetcher
import io.morgaroth.media.library.storage.{DatabaseConfig, DataSourceLive, TracksStoragePostgres, ZioTracksStorageService}
import zio.*
import zio.http.*

/** Single entrypoint for the music library container.
  *
  * Dispatches on the first CLI argument so the same image can be used two ways,
  * both logging to stdout (visible via `docker logs` / the HA add-on log page):
  *
  *   - `serve` (default, and what the add-on / `docker run` with no args uses)
  *       Starts the HTTP server: web UI + JSON API + async fetch jobs.
  *   - `fetch [opts]`
  *       Runs the batch downloader in the foreground over all ready-to-fetch
  *       tracks, then exits. Options are parsed by `Args` (scopt).
  *
  * Postgres connection is read from application.conf, which honors the
  * MUSIC_LIBRARY_POSTGRES_* env vars. Server/output config comes from
  * ServerConfig (env-driven).
  */
object HttpApp extends ZIOAppDefault:

  // Single config source: /data/options.json (HA add-on) -> env -> defaults.
  private val opts: OptionsSource = OptionsSource.load()

  /** Resolve the Postgres connection: a full url wins, else assemble from parts.
    * Values come from options.json / env; falls back to application.conf when
    * nothing is provided (keeps `sbt http/run` working from resources).
    */
  private def databaseConfig: DatabaseConfig =
    opts.str("postgres_url", "MUSIC_LIBRARY_POSTGRES_URL") match
      case Some(url) =>
        DatabaseConfig(
          url = url,
          user = opts.string("postgres_user", "MUSIC_LIBRARY_POSTGRES_USER", "postgres"),
          password = opts.string("postgres_password", "MUSIC_LIBRARY_POSTGRES_PASSWORD", ""),
        )
      case None =>
        val host = opts.str("postgres_host", "MUSIC_LIBRARY_POSTGRES_HOST")
        host match
          case Some(h) =>
            val port = opts.int("postgres_port", "MUSIC_LIBRARY_POSTGRES_PORT", 5432)
            val db = opts.string("postgres_database", "MUSIC_LIBRARY_POSTGRES_DATABASE", "music_library")
            DatabaseConfig(
              url = s"jdbc:postgresql://$h:$port/$db",
              user = opts.string("postgres_user", "MUSIC_LIBRARY_POSTGRES_USER", "postgres"),
              password = opts.string("postgres_password", "MUSIC_LIBRARY_POSTGRES_PASSWORD", ""),
            )
          case None =>
            // Nothing supplied: use application.conf (which itself honors env overrides).
            DatabaseConfig.fromTypesafeConfig(ConfigFactory.load())

  private val configLayer: ZLayer[Any, Throwable, DatabaseConfig] =
    ZLayer.fromZIO(ZIO.attempt(databaseConfig))

  private val storageLayer: ZLayer[Any, Throwable, ZioTracksStorageService] =
    configLayer >>> DataSourceLive.layer >>> TracksStoragePostgres.layer

  private val fetcherLayer: ZLayer[ZioTracksStorageService, Nothing, ZIOFetcher] =
    ZIOFetcher.live

  private val serverConfig: ServerConfig = ServerConfig.fromOptions(opts)

  /** Ensure the configured output/cache directories exist. On an NFS mount this
    * also surfaces permission problems early.
    */
  private val ensureDirs: Task[Unit] =
    ZIO.attempt:
      serverConfig.outputDir.mkdirs()
      serverConfig.cacheDir.mkdirs()
    .unit

  // --- serve mode -----------------------------------------------------------

  private val serveProgram: ZIO[MusicRoutes & Server, Throwable, Unit] =
    for
      routes <- ZIO.service[MusicRoutes]
      _ <- ensureDirs
      _ <- ZIO.logInfo(s"Music library HTTP API + UI listening on :${serverConfig.port}")
      _ <- ZIO.logInfo(s"Fetch output dir: ${serverConfig.outputDir.getAbsolutePath}")
      _ <- Server.serve(routes.routes)
    yield ()

  private val serve: ZIO[Any, Throwable, Unit] =
    serveProgram.provide(
      storageLayer,
      fetcherLayer,
      ZLayer.succeed(serverConfig),
      JobRegistry.live,
      MusicRoutes.live,
      Server.defaultWithPort(serverConfig.port),
    )

  // --- fetch mode -----------------------------------------------------------

  private def fetch(args: List[String]): ZIO[Any, Throwable, Unit] =
    ensureDirs *>
      ZIO.logInfo("Running batch fetch...") *>
      ZIO.serviceWithZIO[ZIOFetcher](_.main(args)).provide(storageLayer, fetcherLayer)

  // --- dispatch -------------------------------------------------------------

  override def run: ZIO[ZIOAppArgs & Scope, Any, Any] =
    for
      args <- getArgs.map(_.toList)
      _ <- args match
        case Nil | ("serve" :: _)  => serve
        case "fetch" :: rest       => fetch(rest)
        case cmd :: _              =>
          ZIO.logError(s"Unknown command '$cmd'. Valid commands: serve (default), fetch") *>
            ZIO.fail(IllegalArgumentException(s"Unknown command: $cmd"))
    yield ()
