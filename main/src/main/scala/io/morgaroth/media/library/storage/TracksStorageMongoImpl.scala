package io.morgaroth.media.library.storage

import com.typesafe.config.ConfigFactory
import io.morgaroth.media.library.storage.ZioTracksStorageService.ZioTracksStorage
import io.morgaroth.media.library.storage.ziosupport.mongoziointerop.ToTask
import io.morgaroth.media.library.storage.ziosupport.{ConnectionInfo, MongoConnectionCollection, MongoConnectionConfig, MongoConnectionUrl, MongoDBConnectionModule}
import org.bson.codecs.configuration.CodecRegistries
import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.bson.codecs.Macros
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.{Filters, FindOneAndUpdateOptions, IndexOptions, Updates}
import org.mongodb.scala.{Document, MongoClient, MongoCollection}
import zio.{Has, IO, RLayer, Task, URLayer, ZIO, ZLayer}

import java.time.ZonedDateTime
import java.util.UUID


object TracksStorageService {

  val live: ZLayer[Has[ConnectionInfo], Throwable, ZioTracksStorage] = ZLayer.fromEffect {
    for {
      hci <- ZIO.access[Has[ConnectionInfo]](_.get)
      col = hci.database.getCollection[Track](hci.config.collectionName)
      db = new TracksStorageMongoImpl(col)
      _ <- db.initialize()
    } yield db
  }

  private val customCodecs = CodecRegistries.fromCodecs(UTCZonedDateTimeMongoCodec, MongoCodec.StringUUIDCodec, MongoCodec.TrackStatusCodec)
  private val customCodecProviders = CodecRegistries.fromProviders(TrackStatusCodecProvider)
  private val initialCodecs = CodecRegistries.fromRegistries(customCodecs, customCodecProviders, MongoClient.DEFAULT_CODEC_REGISTRY)

  private val TrackCodec = Macros.createCodecProviderIgnoreNone[Track]

  private val tracksStorageCodecs = CodecRegistries.fromRegistries(
    initialCodecs,
    CodecRegistries.fromProviders(TrackCodec),
  )

  val tracksStorage: ZLayer[Has[MongoConnectionConfig], Throwable, ZioTracksStorage] =
    MongoDBConnectionModule.forCodec(tracksStorageCodecs) >>> live


  val mong = MongoConnectionConfig(ConfigFactory.load().getString("music-library.mongo.uri"), "Tracks")

  private def LdefinedCollection(colName:String) = ZLayer.fromService((x:MongoConnectionUrl) => MongoConnectionConfig(x.uri,colName))

  val AllTracks: RLayer[Has[MongoConnectionUrl], ZioTracksStorage] = LdefinedCollection("Tracks") >>> tracksStorage
  val ChristmasTracks = LdefinedCollection("ChristmasTracks") >>> tracksStorage
  val ZbysioTracks = LdefinedCollection("ZbysioTracks") >>> tracksStorage
}

class TracksStorageMongoImpl(col: MongoCollection[Track]) extends ZioTracksStorageService {

  object Fields {
    val status = "status"
    val artist = "artist"
    val updatedAt = "updatedAt"
    val title = "title"
    val album = "album"
    val _rawId = "_id"
  }

  def initialize(): Task[Unit] = {
    val options = IndexOptions()
      .partialFilterExpression(Filters.exists("idCheck"))
      .unique(true)

    for {
      _ <- col.createIndex(Document("id" -> 1)).toTask
      _ <- col.createIndex(Document("idCheck" -> 1), options).toTask
    } yield ()
  }

  private def condSet[A](name: String, value: Option[A]) = {
    value.fold(Updates.unset(name))(Updates.set(name, _))
  }

  override def getById(id: UUID) =
    col.find(Filters.eq(Fields._rawId, id)).first().toTask

  override def save(document: Track) =
    col.insertOne(document).collect().toTask.unit

  private def update(id: UUID, updates: Seq[Bson]) = {
    val opts = FindOneAndUpdateOptions()
    opts.upsert(false)
    val value = updates :+ Updates.set(Fields.updatedAt, ZonedDateTime.now())
    col.findOneAndUpdate(Filters.eq(Fields._rawId, id), value, opts).toTask.flatMap {
      case null => Task.fail(new IllegalArgumentException(s"No updates for $id"))
      case _ => Task.succeed(())
    }
  }

  private def updateStrict[A](id: UUID, field: String, value: A) = {
    update(id, Seq(Updates.set(field, value)))
  }

  private def updateOption[A](id: UUID, field: String, value: Option[A]) = {
    update(id, Seq(condSet(field, value)))
  }

  override def store(url: String): IO[Throwable, Track] = {
    val track = Track(url)
    save(track) >>> getById(track._id)
  }

  override def updateArtist(id: UUID, artist: String) = {
    for {
      db <- getById(id)
      _ <- update(id, Seq(Updates.set(Fields.artist, artist), Updates.set("idCheck", TrackId(artist, db.title).get)))
      updated <- getById(id)
    } yield updated
  }

  override def updateTitle(id: UUID, title: String) =
    for {
      db <- getById(id)
      _ <- update(id, Seq(Updates.set(Fields.title, title), Updates.set("idCheck", TrackId(db.artist, title).get)))
      updated <- getById(id)
    } yield updated

  override def updateAlbum(id: UUID, album: String) =
    updateStrict(id, Fields.album, album) >>> getById(id)

  override def updateStartAt(id: UUID, data: Option[String]) =
    updateOption(id, "startAt", data) >>> getById(id)

  override def updateEndAt(id: UUID, data: Option[String]) =
    updateOption(id, "endAt", data) >>> getById(id)

  override def updateUrl(id: UUID, url: String) =
    update(id, Seq(Updates.set("url", url), Updates.set(Fields.status, Draft.dbRepr))) >>> getById(id)

  override def updateStatus(id: UUID, status: TrackStatus) =
    updateStrict(id, Fields.status, status.dbRepr) >>> getById(id)

  override def updateFadeOutSeconds(id: UUID, newData: Option[Int]) =
    updateOption(id, "fadeOutSeconds", newData) >>> getById(id)

  override def updateVolumeChange(id: UUID, newData: Option[BigDecimal]) =
    updateOption(id, "volumeChange", newData) >>> getById(id)


  val hardcodedPlaylistsInsteadOfSmartManagement = Set(
    "electronic", "christmas", "all", "zbysio",
  )

  override def updatePlaylists(id: UUID, newData: Set[String]) =
    for {
      _ <- getById(id)
      _ <- Task.fromEither(Either.cond(newData.intersect(hardcodedPlaylistsInsteadOfSmartManagement).size == newData.size, (), UnknownPlaylist(newData.diff(hardcodedPlaylistsInsteadOfSmartManagement).mkString)))
      _ <- updateStrict(id, "playlists", newData)
      updated <- getById(id)
    } yield updated

  override def updateRawTitle(id: UUID, newData: String) =
    updateStrict(id, "rawTitle", newData) >>> getById(id)

  override def updateRawDescription(id: UUID, newData: String) =
    updateStrict(id, "rawDescription", newData) >>> getById(id)

  override def findAllPlaylists(): IO[Throwable, Map[String, Vector[Track]]] = {
    col.find(Filters.exists("playlists.0"))
      .collect().toTask
      .map(_.toVector.flatMap(x => x.playlists.map(_ -> x)).groupBy(_._1).view.mapValues(_.map(_._2)).toMap)
  }

  override def search(artist: Option[String], title: Option[String], statuses: Option[Set[TrackStatus]], limit: Integer) = {
    val q = Filters.and(List(
      artist.map(str => Filters.regex(Fields.artist, s"(?i).*$str.*")),
      title.map(str => Filters.regex(Fields.title, s"(?i).*$str.*")),
      statuses.map(_.map(_.dbRepr)).map(set => Filters.in(Fields.status, set)),
    ).flatten: _*)
    col.find(q).collect().toTask.map(_.toVector)
  }

  override def findAllReadyToFetch: IO[Throwable, Vector[Track]] = {
    val q = Filters.and(
      Filters.eq(Fields.status, Final.dbRepr),
      Filters.regex(Fields.artist, "(?i).+"),
      Filters.regex(Fields.title, "(?i).+"),
    )
    col.find(q).collect().toTask.map(_.toVector)
  }

  override def genericSearch(text: String, page: Int): IO[Throwable, Vector[Track]] = {
    val filters = Vector(
      Filters.regex("url", s"(?i).*$text.*"),
      Filters.regex(Fields.artist, s"(?i).*$text.*"),
      Filters.regex(Fields.title, s"(?i).*$text.*"),
      Filters.regex(Fields.status, s"(?i).*$text.*"),
      Filters.regex("playlists", s"(?i).*$text.*"),
    )
    val q = if (text.trim.nonEmpty) Filters.or(filters: _*) else BsonDocument()
    col.find(q).skip((page - 1) * 10).limit(10).collect().toTask.map(_.toVector)
  }
}

