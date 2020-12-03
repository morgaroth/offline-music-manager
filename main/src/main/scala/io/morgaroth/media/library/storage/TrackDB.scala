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


class TracksDB(val connectionCfg: Config) extends TracksStorage with LazyLogging {
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
    Either.catchNonFatal(dao.save(document)).flatMap(_ => getById(document._id))
  }

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

