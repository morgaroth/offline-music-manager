package io.morgaroth.media.library.jobs

import com.typesafe.config.ConfigFactory
import io.morgaroth.media.library.ZIOGuiApp
import io.morgaroth.media.library.gui.ZioGuiBackend
import io.morgaroth.media.library.storage.TracksStorageService
import io.morgaroth.media.library.storage.ziosupport.MongoConnectionUrl
import zio._
import zio.magic._


object GUIApp extends zio.App {

  def run(args: List[String]): URIO[ZEnv, ExitCode] = {
    ZIO
      .collectAllPar(
        Seq(
          TracksStorageService.AllTracks.build.useForever.unit,
          ZioGuiBackend.live.build.useForever,
          ZIOGuiApp.live.build.useForever
        ),
      )
      .inject(
        Task.effect {
          println("Loading connection string...")
          MongoConnectionUrl(ConfigFactory.load().getString("music-library.mongo.uri"))
        }.toLayer,
        TracksStorageService.AllTracks,
        ZioGuiBackend.live,
      ).exitCode
  }
}
