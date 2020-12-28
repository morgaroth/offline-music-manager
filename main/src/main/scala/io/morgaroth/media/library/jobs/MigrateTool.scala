package io.morgaroth.media.library.jobs

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import io.morgaroth.media.library.common._
import io.morgaroth.media.library.gui.FutureAwaitable
import io.morgaroth.media.library.storage.TracksDB

import java.time.LocalDateTime

object MigrateTool extends LazyLogging {

  def main(args: Array[String]): Unit = {
    val cfg = ConfigFactory.load()
    val mongoCfg = cfg.getConfig("music-library.mongo")
    val mongoCfg2 = cfg.getConfig("music-library.mongo2")
    val storage = new TracksDB(mongoCfg)
    val storage2 = new TracksDB(mongoCfg2)
    storage.all.await()
      .filter(_.createdAtLocal.isAfter(LocalDateTime.of(2019, 12, 10, 10, 0, 0)))
      .filter(_.createdAtLocal.isBefore(LocalDateTime.of(2020, 1, 10, 10, 0, 0)))
      .sortBy(_.createdAt)
      .foreach(storage2.save)
  }
}
