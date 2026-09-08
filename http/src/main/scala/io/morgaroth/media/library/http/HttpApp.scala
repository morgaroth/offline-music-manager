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

  private val configLayer: ZLayer[Any, Throwable, DatabaseConfig] =
    ZLayer.fromZIO:
      ZIO.attempt(DatabaseConfig.fromTypesafeConfig(ConfigFactory.load()))

  private val storageLayer: ZLayer[Any, Throwable, ZioTracksStorageService] =
    configLayer >>> DataSourceLive.layer >>> TracksStoragePostgres.layer

  private val fetcherLayer: ZLayer[ZioTracksStorageService, Nothing, ZIOFetcher] =
    ZIOFetcher.live

  private val serverConfig: ServerConfig = ServerConfig.fromEnv

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
      ServerConfig.live,
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
