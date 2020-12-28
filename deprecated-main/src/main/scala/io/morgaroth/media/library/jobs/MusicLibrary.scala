package io.morgaroth.media.library.jobs

import io.morgaroth.media.library.storage.MongoRepo

object MusicLibrary {
  def main(args: Array[String]): Unit = {
    val storage = MongoRepo.fromArgs()
    val handler = new BaseMusicLibrary(storage)
    handler.main(args)
  }
}
