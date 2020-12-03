package io.morgaroth.media.library.jobs

import com.typesafe.config.ConfigFactory
import io.morgaroth.media.library.storage.TracksDB

import java.io.File

object ChristmasSongsFetch {
  def main(args: Array[String]): Unit = {
    val file = new File(new File(new File(System.getProperty("user.home")), "music-library"), "christmas")

    val tcfg = ConfigFactory.load()
    val mongoCfg = tcfg.getConfig("music-library.mongo2")
    val storage = new TracksDB(mongoCfg)
    new Boot(storage).main(Array("--destination-dir", file.getAbsolutePath))
  }
}
