package io.morgaroth.media.library.storage

import com.typesafe.config.Config
import io.morgaroth.testing.docker.mongo.MongoSupport
import org.scalatest.{FlatSpec, Inside, Matchers}

class TracksDBTest extends FlatSpec with Matchers with MongoSupport[TracksDB] with Inside {
  override def setUpMongoDataBase(cfg: Config): TracksDB = {
    new TracksDB(cfg)
  }

  "TracksDB" should "use playlists array in generic search" in {
    store(1)
    store(2, playlists = Set("playlist-2"))
    store(3, playlists = Set("no-3"))
    store(4, playlists = Set("playlist-4", "noop-4"))

    val value = MongoDB.genericSearch("aylis", 1)
    value shouldBe 'right
    value.right.get.length shouldBe 2
    value.right.get.map(_.url) should contain only("url-2", "url-4")
  }

  it should "return correct map of playlists" in {
    store(1, playlists = Set("playlist-1", "playlist-2"))
    store(2, playlists = Set("playlist-2"))
    store(3, playlists = Set("playlist-3"))
    store(4, playlists = Set("playlist-1", "playlist-4"))
    store(5)

    val value = MongoDB.findAllPlaylists()
    value shouldBe 'right
    value.right.get should have size 4
    value.right.get("playlist-1") should have size 2
    value.right.get("playlist-1").map(_.url) should contain only("url-4", "url-1")

    value.right.get("playlist-2") should have size 2
    value.right.get("playlist-2").map(_.url) should contain only("url-2", "url-1")

    value.right.get("playlist-3") should have size 1
    value.right.get("playlist-3").map(_.url) should contain only "url-3"

    value.right.get("playlist-4") should have size 1
    value.right.get("playlist-4").map(_.url) should contain only "url-4"
  }


  def genTrack(num: Int, status: TrackStatus = Final, playlists: Set[String] = Set.empty) = {
    Track(s"url-$num", s"title-$num", s"artist-$num", s"album-$num", status, None, None, None, None, playlists)
  }

  def store(num: Int, status: TrackStatus = Final, playlists: Set[String] = Set.empty) = {
    MongoDB.save(genTrack(num, status, playlists)) shouldBe 'right
  }
}
