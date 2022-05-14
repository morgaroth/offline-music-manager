package io.morgaroth.media.library.jobs

import io.morgaroth.media.library.gui.ZioGuiBackend
import io.morgaroth.media.library.gui.windows.AppWindow
import io.morgaroth.media.library.storage.ZioTracksStorageService.ZioTracksStorage
import io.morgaroth.media.library.storage.ziosupport.MongoConnectionConfig
import io.morgaroth.media.library.storage.{MongoRepo, TracksStorageService}
import org.gnome.gtk.Gtk
import zio.{ULayer, ZLayer}


class ZIOGuiApp(layer: ULayer[ZioTracksStorage]) {
  def main(): Unit = {
    Gtk.init(Array.empty[String])
    val backend = new ZioGuiBackend(layer)
    val guiApp = new AppWindow(backend)
    guiApp.show()
  }
}

object GUIApp {
  def main(args: Array[String]): Unit = {
//    val storage = MongoRepo.AllTracks()
    val url =
      s"mongodb+srv://stock-monitor-user:${sys.env("STOCK_MONITOR_USER_PASSWORD")}@morgaroth.rtnyg.mongodb.net/Tracks?retryWrites=true&w=majority"
    val mong = MongoConnectionConfig(url, "Tracks")
    val storage = ZLayer.succeed(mong) >>> TracksStorageService.tracksStorage
    val gui = new ZIOGuiApp(storage.orDie)
    gui.main()
  }
}
