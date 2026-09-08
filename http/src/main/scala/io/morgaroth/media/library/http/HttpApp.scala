package io.morgaroth.media.library.http

import com.typesafe.config.ConfigFactory
import io.morgaroth.media.library.jobs.ZIOFetcher
import io.morgaroth.media.library.storage.{DatabaseConfig, DataSourceLive, TracksStoragePostgres, ZioTracksStorageService}
import zio.*
import zio.http.*

/** Headless HTTP entrypoint for the music library.
  *
  * Reuses the exact layer stack from `jobs.Boot` (config -> datasource ->
  * postgres storage -> fetcher) and adds the job registry, routes, and server.
  * The port can be overridden with MUSIC_LIBRARY_HTTP_PORT (default 8080).
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

  /** Ensure the configured output/cache directories exist before serving.
    * On an NTFS mount this also surfaces permission problems early.
    */
  private val ensureDirs: Task[Unit] =
    ZIO.attempt:
      serverConfig.outputDir.mkdirs()
      serverConfig.cacheDir.mkdirs()
    .unit

  private val program: ZIO[MusicRoutes & Server, Throwable, Unit] =
    for
      routes <- ZIO.service[MusicRoutes]
      _ <- ensureDirs
      _ <- ZIO.logInfo(s"Music library HTTP API + UI listening on :${serverConfig.port}")
      _ <- ZIO.logInfo(s"Fetch output dir: ${serverConfig.outputDir.getAbsolutePath}")
      _ <- Server.serve(routes.routes)
    yield ()

  override def run: ZIO[ZIOAppArgs & Scope, Any, Any] =
    program.provide(
      storageLayer,
      fetcherLayer,
      ServerConfig.live,
      JobRegistry.live,
      MusicRoutes.live,
      Server.defaultWithPort(serverConfig.port),
    )
