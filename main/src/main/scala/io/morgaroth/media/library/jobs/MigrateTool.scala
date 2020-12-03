package io.morgaroth.media.library.jobs

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import io.morgaroth.media.library.storage.TracksDB
import org.joda.time.LocalDateTime


object MigrateTool extends LazyLogging {

  def main(args: Array[String]): Unit = {
    val cfg = ConfigFactory.load()
    val mongoCfg = cfg.getConfig("music-library.mongo")
    val mongoCfg2 = cfg.getConfig("music-library.mongo2")
    val storage = new TracksDB(mongoCfg)
    val storage2 = new TracksDB(mongoCfg2)
    storage.all
      .filter(_.createdAt.isAfter(new LocalDateTime(2019, 12, 10, 10, 0, 0)))
      .filter(_.createdAt.isBefore(new LocalDateTime(2020, 1, 10, 10, 0, 0)))
      .sortBy(_.createdAt.toDateTime.toInstant.getMillis)
      .foreach(storage2.save)
  }
}
