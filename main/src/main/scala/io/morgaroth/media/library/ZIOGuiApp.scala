package io.morgaroth.media.library

import io.morgaroth.media.library.gui.ZioGuiBackend
import io.morgaroth.media.library.gui.windows.AppWindow
import org.gnome.gtk.Gtk
import zio.{Has, ZLayer}

class ZIOGuiApp(backend: ZioGuiBackend) {
  Gtk.init(Array.empty[String])
  val guiApp = new AppWindow(backend)
  guiApp.show()
}

object ZIOGuiApp {
  val live: ZLayer[Has[ZioGuiBackend], Nothing, Has[ZIOGuiApp]] = ZLayer.fromService(new ZIOGuiApp(_))
}
