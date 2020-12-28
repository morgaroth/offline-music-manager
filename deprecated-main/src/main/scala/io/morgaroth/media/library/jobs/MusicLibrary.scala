package io.morgaroth.media.library.jobs

import com.typesafe.config.ConfigFactory
import io.morgaroth.media.library.storage.TracksDB

object MusicLibrary {
  def main(args: Array[String]): Unit = {
    val cfg = ConfigFactory.load()
    val mongoCfg = cfg.getConfig("music-library.mongo")
    val storage = new TracksDB(mongoCfg)
    val handler = new BaseMusicLibrary(storage)
    handler.main(args)
  }
}
