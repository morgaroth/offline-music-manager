package io.morgaroth.media.library.jobs

import io.morgaroth.media.library.storage.MongoRepo

import java.io.File

object AllSongsFetch {
  def main(args: Array[String]): Unit = {
    val file = new File(new File(new File(System.getProperty("user.home")), "music-library"), "all")

    val storage = MongoRepo.AllTracks
    new Fetcher(storage).main(Array("--destination-dir", file.getAbsolutePath))
  }
}
