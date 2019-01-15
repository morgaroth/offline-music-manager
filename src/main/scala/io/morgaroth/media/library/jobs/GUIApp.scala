package io.morgaroth.media.library.jobs

import com.typesafe.config.ConfigFactory
import io.morgaroth.media.library.gui.{AppWindow, MongoBackedGuiBackend}
import io.morgaroth.media.library.storage.TracksDB
import org.gnome.gtk.Gtk

object GUIApp {
  def main(args: Array[String]): Unit = {
    Gtk.init(args)
    val cfg = ConfigFactory.load()
    val mongoCfg = cfg.getConfig("music-library.mongo")
    val storage = new TracksDB(mongoCfg)
    val backend = new MongoBackedGuiBackend(storage)
    val guiApp = new AppWindow(backend)
    guiApp.show()
  }
}
