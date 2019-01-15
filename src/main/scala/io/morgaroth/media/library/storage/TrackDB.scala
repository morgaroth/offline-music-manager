package io.morgaroth.media.library.storage

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
                  startAt: Option[String],
                  endAt: Option[String],
                  fadeOutSeconds: Option[Int],
                  status: TrackStatus,
                  idCheck: Option[String],
                  updatedAt: LocalDateTime = LocalDateTime.now(),
                  createdAt: LocalDateTime = LocalDateTime.now(),
                  @Key("_id") id: UUID = UUID.randomUUID(),
                ) {
  lazy val info = s"$artist - $title"
}

object Track {
  def apply(
             url: String,
             title: String,
             artist: String,
             status: TrackStatus,
           ): Track = {
    new Track(url, title, artist, none, none, none, status, TrackId(artist, title))
  }

  def apply(
             url: String,
             title: String,
             artist: String,
             status: TrackStatus,
             startAt: Option[String],
             endAt: Option[String],
             fadeOutSeconds: Option[Int],
           ): Track = {
    new Track(url, title, artist, startAt, endAt, fadeOutSeconds, status, TrackId(artist, title))
  }

  def apply(url: String): Track = {
    new Track(url, "", "", None, None, None, Draft, None)
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

  private def updateFields(id: UUID, kv: (String, Any), kvRest: (String, Any)*): ErrorOr[Imports.WriteResult] = {
    val updateQuery = MongoDBObject(kv._1 -> kv._2)
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

    Either.catchNonFatal(limitOpt.map(dao.find(q).take(_)).getOrElse(dao.find(q)).toVector)
  }

  def genericSearch(text: String, page: Int): ErrorOr[Vector[Track]] = {
    val q = MongoDBObject("$or" -> List(
      "url" -> s"(?i).*$text.*".r,
      "artist" -> s"(?i).*$text.*".r,
      "title" -> s"(?i).*$text.*".r,
      "status" -> s"(?i).*$text.*".r,
    ).map(MongoDBObject(_)))

    Either.catchNonFatal(dao.find(q).slice((page - 1) * 10, page * 10).toVector)
  }
}

