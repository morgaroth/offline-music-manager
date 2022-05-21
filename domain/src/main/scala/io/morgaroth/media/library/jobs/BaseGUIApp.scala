package io.morgaroth.media.library.jobs

import io.morgaroth.media.library.gui.FutureGuiBackend
import io.morgaroth.media.library.gui.windows.AppWindow
import io.morgaroth.media.library.storage.TracksStorage
import org.gnome.gtk.Gtk

import scala.concurrent.Future

class BaseGUIApp(storage: TracksStorage[Future]) {
  def main(): Unit = {
    Gtk.init(Array.empty[String])
    val backend = new FutureGuiBackend(storage)
    val guiApp = new AppWindow(backend)
    guiApp.show()
  }
}
