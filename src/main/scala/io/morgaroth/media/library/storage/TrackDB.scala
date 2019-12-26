package io.morgaroth.media.library.storage

import java.net.URLEncoder
import java.util.UUID

import cats.implicits._
import com.mongodb.casbah.Imports
import com.mongodb.casbah.commons.MongoDBObject
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import io.github.morgaroth.utils.mongodb.salat.MongoDAOJodaSupport
import io.morgaroth.media.library.ErrorOr
import org.joda.time.LocalDateTime
import salat.annotations.Key

case class TrackNotFound(desc: String) extends Exception(s"track not found $desc")

case class Track(
                  url: String,
                  title: String,
                  artist: String,
                  album: String,
                  startAt: Option[String],
                  endAt: Option[String],
                  fadeOutSeconds: Option[Int],
                  volumeChange: Option[BigDecimal],
                  status: TrackStatus,
                  idCheck: Option[String],
                  playlists: Set[String] = Set.empty,
                  rawTitle: Option[String] = Some(""),
                  rawDescription: Option[String] = Some(""),
                  updatedAt: LocalDateTime = LocalDateTime.now(),
                  createdAt: LocalDateTime = LocalDateTime.now(),
                  @Key("_id") id: UUID = UUID.randomUUID(),
                ) {
  lazy val searchUrl = s"https://www.youtube.com/results?search_query=${URLEncoder.encode(s"$title $artist", "utf-8")}"

  lazy val isReadyToFetch: Boolean = title.nonEmpty && artist.nonEmpty && status == Final

  lazy val info = s"$artist - $title"
  lazy val UFID: String = io.morgaroth.media.library.md5HashString(s"$url$title$artist$album$startAt$endAt$fadeOutSeconds:$volumeChange")
}

object Track {
  def apply(
             url: String,
             title: String,
             artist: String,
             status: TrackStatus,
           ): Track = {
    apply(url, title, artist, "", status)
  }

  def apply(
             url: String,
             title: String,
             artist: String,
             album: String,
             status: TrackStatus,
           ): Track = {
    new Track(url, title, artist, album, none, none, none, none, status, TrackId(artist, title), Set.empty)
  }

  def apply(
             url: String,
             title: String,
             artist: String,
             album: String,
             status: TrackStatus,
             startAt: Option[String],
             endAt: Option[String],
             fadeOutSeconds: Option[Int],
             volumeChange: Option[BigDecimal],
             playlists: Set[String],
           ): Track = {
    new Track(url, title, artist, album, startAt, endAt, fadeOutSeconds, volumeChange, status, TrackId(artist, title), playlists)
  }

  def apply(url: String): Track = {
    new Track(url, "", "", "", None, None, None, None, Draft, None, Set.empty)
  }
}

object TrackId {
  val sep = "___"

  def apply(artist: String, title: String): Option[String] =
    if (title.nonEmpty || artist.nonEmpty) s"$artist$sep$title".some else none
}

class TracksDB(val connectionCfg: Config) extends LazyLogging {
  def all: Vector[Track] = dao.find(MongoDBObject.empty).toVector

  UUIDConversionHelpers.register()

  private val dao = new MongoDAOJodaSupport[Track](connectionCfg, "Tracks")
  dao.collection.createIndex("id")
  dao.collection.createIndex(
    MongoDBObject("idCheck" -> 1),
    MongoDBObject(
      "partialFilterExpression" -> MongoDBObject("idCheck" -> MongoDBObject("$exists" -> true)),
      "unique" -> true,
    )
  )


  def getById(id: UUID): ErrorOr[Track] =
    Either.catchNonFatal(dao.findOne(MongoDBObject("_id" -> id))).flatMap(
      _.map(_.asRight).getOrElse(TrackNotFound(s"by id $id").asLeft)
    )

  def getBy(artist: String, title: String): ErrorOr[Track] =
    Either.catchNonFatal(dao.findOne(MongoDBObject("artist" -> artist, "title" -> title))).flatMap(
      _.map(_.asRight).getOrElse(TrackNotFound(s"by artist/title $artist/$title").asLeft)
    )

  def save(document: Track): ErrorOr[Track] = {
    Either.catchNonFatal(dao.save(document)).flatMap(_ => getById(document.id))
  }

  def store(url: String): ErrorOr[Track] = save(Track(url))

  private def updateFields(id: UUID, kv: (String, AnyRef), kvRest: (String, AnyRef)*): ErrorOr[Imports.WriteResult] = {
    val updateQuery = MongoDBObject("updatedAt" -> LocalDateTime.now(), kv)
    kvRest.foreach(x => updateQuery.put(x._1, x._2))

    Either.catchNonFatal(dao.update(
      MongoDBObject("_id" -> id),
      MongoDBObject("$set" -> updateQuery),
      upsert = false
    )).flatMap(x => if (x.getN == 0) TrackNotFound(s"by id $id").asLeft else x.asRight)
  }

  def updateArtist(id: UUID, artist: String): ErrorOr[Track] = {
    for {
      db <- getById(id)
      _ <- updateFields(id, "artist" -> artist, "idCheck" -> TrackId(artist, db.title).get)
      res <- getById(id)
    } yield res
  }

  def updateTitle(id: UUID, title: String): ErrorOr[Track] = {
    for {
      db <- getById(id)
      _ <- updateFields(id, "title" -> title, "idCheck" -> TrackId(db.artist, title).get)
      res <- getById(id)
    } yield res
  }

  def updateAlbum(id: UUID, album: String): ErrorOr[Track] = {
    updateFields(id, "album" -> album) >> getById(id)
  }

  def updateStartAt(id: UUID, data: Option[String]): ErrorOr[Track] = {
    updateFields(id, "startAt" -> data) >> getById(id)
  }

  def updateEndAt(id: UUID, data: Option[String]): ErrorOr[Track] = {
    updateFields(id, "endAt" -> data) >> getById(id)
  }

  def updateUrl(id: UUID, url: String): ErrorOr[Track] = {
    updateFields(id, "url" -> url, "status" -> Draft.dbRepr) >> getById(id)
  }

  def updateStatus(id: UUID, status: TrackStatus): ErrorOr[Track] = {
    updateFields(id, "status" -> status.dbRepr) >> getById(id)
  }

  def updateFadeOutSeconds(id: UUID, newData: Option[Int]): ErrorOr[Track] = {
    updateFields(id, "fadeOutSeconds" -> newData) >> getById(id)
  }

  def updateVolumeChange(id: UUID, newData: Option[BigDecimal]): ErrorOr[Track] = {
    updateFields(id, "volumeChange" -> newData.map(_.toDouble)) >> getById(id)
  }

  def updatePlaylists(id: UUID, newData: Set[String]): ErrorOr[Track] = {
    updateFields(id, "playlists" -> newData) >> getById(id)
  }

  def updateRawTitle(id: UUID, newData: Option[String]): ErrorOr[Track] = {
    updateFields(id, "rawTitle" -> newData) >> getById(id)
  }

  def updateRawDescription(id: UUID, newData: Option[String]): ErrorOr[Track] = {
    updateFields(id, "rawDescription" -> newData) >> getById(id)
  }

  def findAllPlaylists(): ErrorOr[Map[String, Vector[Track]]] = {
    Either.catchNonFatal(dao.find(MongoDBObject("playlists.0" -> MongoDBObject("$exists" -> true))).toVector)
      .map(_.flatMap(x => x.playlists.map(_ -> x)).groupBy(_._1).mapValues(_.map(_._2)))
  }

  def search(
              artist: Option[String] = None, title: Option[String] = None,
              statuses: Option[Set[TrackStatus]] = None,
              limit: java.lang.Integer = null
            ): ErrorOr[Vector[Track]] = {
    val q = List(
      artist.map(str => "artist" -> s"(?i).*$str.*".r),
      title.map(str => "title" -> s"(?i).*$str.*".r),
      statuses.map(_.map(_.dbRepr)).map(set => "status" -> MongoDBObject("$in" -> set)),
    ).flatten.foldLeft(MongoDBObject.newBuilder) {
      case (acc, c) => acc += c
    }.result()
    val limitOpt = Option(limit).map(_.intValue())

    Either.catchNonFatal(dao.find(q).toVector)
  }

  def findAllReadyToFetch: Vector[Track] = {
    val q = MongoDBObject(
      "artist" -> s"(?i).+".r,
      "title" -> s"(?i).+".r,
      "status" -> Final.dbRepr
    )
    dao.find(q).toVector
  }

  def genericSearch(text: String, page: Int): ErrorOr[Vector[Track]] = {
    val q = MongoDBObject("$or" -> List(
      "url" -> s"(?i).*$text.*".r,
      "artist" -> s"(?i).*$text.*".r,
      "title" -> s"(?i).*$text.*".r,
      "status" -> s"(?i).*$text.*".r,
      "playlists" -> s"(?i).*$text.*".r,
    ).map(MongoDBObject(_)))

    Either.catchNonFatal(dao.find(q).slice((page - 1) * 10, page * 10).toVector)
  }
}

