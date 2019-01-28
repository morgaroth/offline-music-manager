package io.morgaroth.media.library.jobs

import java.io.File

import com.typesafe.config.ConfigFactory
import io.morgaroth.media.library.YamlParser
import io.morgaroth.media.library.storage.{Draft, Final, Track, TracksDB}

class MigrateFromFile {
  def main(args: Array[String]): Unit = {

    val statuses = Map(true -> Draft, false -> Final)

    val urlCfg = ConfigFactory.load()
    val mongoConfig = urlCfg.getConfig("music-library.mongo")
    val db = new TracksDB(mongoConfig)
    val definitions = YamlParser.load(new File("/home/morgaroth/projects/MusicLibrary/definitions.yaml"))
    definitions.foreach { d =>
      val status = statuses(d.draft)
      val track = Track(d.sourceUrl, d.title, d.author, status, d.startAt, d.endAt, d.fadeOutSeconds)
      db.save(track)
    }

  }
}
