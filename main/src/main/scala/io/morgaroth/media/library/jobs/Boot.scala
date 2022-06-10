package io.morgaroth.media.library.jobs

import com.typesafe.config.ConfigFactory
import io.morgaroth.media.library.ZIOGuiApp
import io.morgaroth.media.library.gui.ZioGuiBackend
import io.morgaroth.media.library.storage.TracksStorageService
import io.morgaroth.media.library.storage.ziosupport.MongoConnectionUrl
import zio._
import zio.console.putStrLn
import zio.magic._


object Boot extends zio.App {

  private val layer = Task.effect {
    println("Loading connection string...")
    MongoConnectionUrl(ConfigFactory.load().getString("music-library.mongo.uri"))
  }.toLayer
  lazy val guiApp = ZIO
    .collectAllPar(
      Seq(
        TracksStorageService.AllTracks.build.useForever.unit,
        ZioGuiBackend.live.build.useForever,
        ZIOGuiApp.live.build.useForever,
      ),
    )
    .inject(
      layer,
      TracksStorageService.AllTracks,
      ZioGuiBackend.live,
    ).exitCode

  //  val fetcherApp: ZLayer[Any, Throwable, Has[ZIOFetcher]] =
  //    layer >>> TracksStorageService.AllTracks >>> ZIOFetcher.live

  def fetcherApp(args: List[String]) = ZIO
    .collectAllPar(
      Seq(
        TracksStorageService.AllTracks.build.useNow.unit,
        ZIOFetcher.live.build.useNow,
        ZIO.service[ZIOFetcher].flatMap(_.main(args)),
      ),
    )
    .inject(
      zio.console.Console.live,
      layer,
      TracksStorageService.AllTracks,
      ZIOFetcher.live,
    ).exitCode

  def run(args: List[String]): URIO[ZEnv, ExitCode] = {
    args.headOption match {
      case Some("fetch" | "boot" | "work") =>
        fetcherApp(args.tail)
      case Some("gui") | None =>
        guiApp
      case e =>
        (putStrLn(s"Unknown command '$e', valid are: fetch, boot, work, gui, `no command`.") <* ZIO.fail(???)).exitCode
    }
  }
}
