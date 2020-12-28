package io.morgaroth.media.library.jobs

import com.typesafe.scalalogging.LazyLogging
import io.morgaroth.media.library.storage.MongoRepo

object MigrateTool extends LazyLogging {

  def main(args: Array[String]): Unit = {
    val storage2 = MongoRepo.ZbysioTracks()

    storage2.close()
  }
}
