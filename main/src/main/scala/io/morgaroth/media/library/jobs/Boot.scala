package io.morgaroth.media.library.jobs

import com.typesafe.config.ConfigFactory
import io.morgaroth.media.library.storage.{DatabaseConfig, DataSourceLive, TracksStoragePostgres, ZioTracksStorageService}
import zio.*

object Boot extends ZIOAppDefault:

  private val configLayer: ZLayer[Any, Throwable, DatabaseConfig] =
    ZLayer.fromZIO:
      ZIO.attempt(DatabaseConfig.fromTypesafeConfig(ConfigFactory.load()))

  private val storageLayer: ZLayer[Any, Throwable, ZioTracksStorageService] =
    configLayer >>> DataSourceLive.layer >>> TracksStoragePostgres.layer

  private val fetcherLayer: ZLayer[Any, Throwable, ZIOFetcher] =
    storageLayer >>> ZIOFetcher.live

  private def fetcherApp(args: List[String]): ZIO[Any, Throwable, Unit] =
    ZIO.serviceWithZIO[ZIOFetcher](_.main(args)).provide(fetcherLayer)

  override def run: ZIO[ZIOAppArgs, Any, Any] =
    for
      args <- getArgs.map(_.toList)
      _ <- args.headOption match
        case Some("fetch" | "boot" | "work") =>
          fetcherApp(args.tail)
        case Some("gui") | None =>
          ZIO.logInfo("GUI mode — run the MusicLibraryApp from second-impl module")
        case Some(cmd) =>
          ZIO.logError(s"Unknown command '$cmd', valid are: fetch, boot, work, gui") *>
            ZIO.fail(IllegalArgumentException(s"Unknown command: $cmd"))
    yield ()
