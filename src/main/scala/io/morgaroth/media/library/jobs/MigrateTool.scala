//package io.morgaroth.media.library.jobs
//
//import com.typesafe.config.ConfigFactory
//import com.typesafe.scalalogging.LazyLogging
//import io.morgaroth.media.library.storage.TracksDB
//
//object MigrateTool extends LazyLogging {
//
//  def main(args: Array[String]): Unit = {
//    val cfg = ConfigFactory.load()
//    val mongoCfg = cfg.getConfig("music-library.mongo")
//    val storage = new TracksDB(mongoCfg)
//    storage.all.foreach { tr =>
//      storage.updateAlbum(tr.id, tr.album)
//    }
//  }
//}
