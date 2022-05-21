package io.morgaroth.media.library.jobs

import com.typesafe.config.ConfigFactory
import io.morgaroth.media.library.gui.ZioGuiBackend
import io.morgaroth.media.library.gui.windows.AppWindow
import io.morgaroth.media.library.storage.TracksStorageService
import io.morgaroth.media.library.storage.ZioTracksStorageService.ZioTracksStorage
import io.morgaroth.media.library.storage.ziosupport.MongoConnectionUrl
import org.gnome.gtk.Gtk
import zio._


class ZIOGuiApp(backend: ZioGuiBackend) {
  Gtk.init(Array.empty[String])
  val guiApp = new AppWindow(backend)
  guiApp.show()
}

object GUIApp extends zio.App {

  val mong = Task.effect(MongoConnectionUrl(ConfigFactory.load().getString("music-library.mongo.uri"))).toLayer
  val storage = mong >>> TracksStorageService.AllTracks
  val guiBackendL: ZLayer[ZioTracksStorage, Nothing, Has[ZioGuiBackend]] = ZLayer.fromService(new ZioGuiBackend(_))
  val guiBackend = storage >>> guiBackendL
  val guiAppL: ZLayer[Has[ZioGuiBackend], Nothing, Has[ZIOGuiApp]] = ZLayer.fromService(new ZIOGuiApp(_))
  val gui: ZLayer[Any, Throwable, Has[ZIOGuiApp]] = guiBackend >>> guiAppL
  val program = gui.build.useForever

  def run(args: List[String]): URIO[ZEnv, ExitCode] = program.exitCode
}
