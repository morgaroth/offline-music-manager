package io.morgaroth.media.library.jobs

import io.morgaroth.media.library.storage.MongoRepo

import java.io.File

object ChristmasSongsFetch {
  def main(args: Array[String]): Unit = {
    val file = new File(new File(new File(System.getProperty("user.home")), "music-library"), "christmas")

    val storage = MongoRepo.ChristmasTracks
    new Boot(storage).main(Array("--destination-dir", file.getAbsolutePath))
  }
}
