package io.morgaroth.media.library.jobs

import io.morgaroth.media.library.gui.GuiBackend
import io.morgaroth.media.library.gui.windows.AppWindow
import io.morgaroth.media.library.storage.TracksStorage
import org.gnome.gtk.Gtk

class BaseGUIApp(storage: TracksStorage) {
  def main(): Unit = {
    Gtk.init(Array.empty[String])
    val backend = new GuiBackend(storage)
    val guiApp = new AppWindow(backend)
    guiApp.show()
  }
}
