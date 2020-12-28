package io.morgaroth.media.library.storage

import cats.implicits._
import com.mongodb.casbah.Imports
import com.mongodb.casbah.commons.MongoDBObject
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import io.github.morgaroth.utils.mongodb.salat.MongoDAO
import io.morgaroth.media.library.ErrorOr

import java.time.{LocalDateTime, ZonedDateTime}
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class TracksDB(val connectionCfg: Config) extends TracksStorage[Future] with LazyLogging {
  def all: Future[Vector[Track]] = Future.successful(dao.find(MongoDBObject.empty).toVector)

  UUIDConversionHelpers.register()
  JavaZonedDateTimeHelpers.register()

  private val dao: MongoDAO[Track] = new MongoDAO[Track](connectionCfg, "Tracks")
  dao.collection.createIndex("id")
  dao.collection.createIndex(
    MongoDBObject("idCheck" -> 1),
    MongoDBObject(
      "partialFilterExpression" -> MongoDBObject("idCheck" -> MongoDBObject("$exists" -> true)),
      "unique" -> true,
    )
  )


  def getById(id: UUID): Future[ErrorOr[Track]] = 
    Future.successful(Either.catchNonFatal(dao.findOne(MongoDBObject("_id" -> id))).flatMap(
      _.map(_.asRight).getOrElse(TrackNotFound(s"by id $id").asLeft)
    ))

  private def getByIdPrv(id: UUID): ErrorOr[Track] =
    Either.catchNonFatal(dao.findOne(MongoDBObject("_id" -> id))).flatMap(
      _.map(_.asRight).getOrElse(TrackNotFound(s"by id $id").asLeft)
    )

  def getBy(artist: String, title: String): Future[ErrorOr[Track]] =
    Future.successful(Either.catchNonFatal(dao.findOne(MongoDBObject("artist" -> artist, "title" -> title))).flatMap(
      _.map(_.asRight).getOrElse(TrackNotFound(s"by artist/title $artist/$title").asLeft)
    ))

  def save(document: Track): Future[ErrorOr[Track]] = {
    Future.successful(Either.catchNonFatal(dao.save(document))).flatMap(_ => getById(document._id))
  }

  private def updateFields(id: UUID, kv: (String, AnyRef), kvRest: (String, AnyRef)*): ErrorOr[Imports.WriteResult] = {
    val updateQuery = MongoDBObject("updatedAt" -> ZonedDateTime.now(), kv)
    kvRest.foreach(x => updateQuery.put(x._1, x._2))

    Either.catchNonFatal(dao.update(
      MongoDBObject("_id" -> id),
      MongoDBObject("$set" -> updateQuery),
      upsert = false
    )).flatMap(x => if (x.getN == 0) TrackNotFound(s"by id $id").asLeft else x.asRight)
  }

  def updateArtist(id: UUID, artist: String): Future[ErrorOr[Track]] = {
    Future.successful(for {
      db <- getByIdPrv(id)
      _ <- updateFields(id, "artist" -> artist, "idCheck" -> TrackId(artist, db.title).get)
      res <- getByIdPrv(id)
    } yield res)
  }

  def updateTitle(id: UUID, title: String): Future[ErrorOr[Track]] = {
    Future.successful(for {
      db <- getByIdPrv(id)
      _ <- updateFields(id, "title" -> title, "idCheck" -> TrackId(db.artist, title).get)
      res <- getByIdPrv(id)
    } yield res)
  }

  def updateAlbum(id: UUID, album: String): Future[ErrorOr[Track]] = Future.successful{
    updateFields(id, "album" -> album) >> getByIdPrv(id)
  }

  def updateStartAt(id: UUID, data: Option[String]): Future[ErrorOr[Track]] = Future.successful{
    updateFields(id, "startAt" -> data) >> getByIdPrv(id)
  }

  def updateEndAt(id: UUID, data: Option[String]): Future[ErrorOr[Track]] = Future.successful{
    updateFields(id, "endAt" -> data) >> getByIdPrv(id)
  }

  def updateUrl(id: UUID, url: String): Future[ErrorOr[Track]] = Future.successful{
    updateFields(id, "url" -> url, "status" -> Draft.dbRepr) >> getByIdPrv(id)
  }

  def updateStatus(id: UUID, status: TrackStatus): Future[ErrorOr[Track]] = Future.successful{
    updateFields(id, "status" -> status.dbRepr) >> getByIdPrv(id)
  }

  def updateFadeOutSeconds(id: UUID, newData: Option[Int]): Future[ErrorOr[Track]] = Future.successful{
    updateFields(id, "fadeOutSeconds" -> newData) >> getByIdPrv(id)
  }

  def updateVolumeChange(id: UUID, newData: Option[BigDecimal]): Future[ErrorOr[Track]] =Future.successful{
    updateFields(id, "volumeChange" -> newData.map(_.toDouble)) >> getByIdPrv(id)
  }

  def updatePlaylists(id: UUID, newData: Set[String]): Future[ErrorOr[Track]] =Future.successful{
    updateFields(id, "playlists" -> newData) >> getByIdPrv(id)
  }

  def updateRawTitle(id: UUID, newData: Option[String]): Future[ErrorOr[Track]] = Future.successful{
    updateFields(id, "rawTitle" -> newData) >> getByIdPrv(id)
  }

  def updateRawDescription(id: UUID, newData: Option[String]): Future[ErrorOr[Track]] = Future.successful{
    updateFields(id, "rawDescription" -> newData) >> getByIdPrv(id)
  }

  def findAllPlaylists(): Future[ErrorOr[Map[String, Vector[Track]]]] = Future.successful{
    Either.catchNonFatal(dao.find(MongoDBObject("playlists.0" -> MongoDBObject("$exists" -> true))).toVector)
      .map(_.flatMap(x => x.playlists.map(_ -> x)).groupBy(_._1).mapValues(_.map(_._2)))
  }

  def search(
              artist: Option[String] = None, title: Option[String] = None,
              statuses: Option[Set[TrackStatus]] = None,
              limit: java.lang.Integer = null
            ): Future[ErrorOr[Vector[Track]]] = {
    val q = List(
      artist.map(str => "artist" -> s"(?i).*$str.*".r),
      title.map(str => "title" -> s"(?i).*$str.*".r),
      statuses.map(_.map(_.dbRepr)).map(set => "status" -> MongoDBObject("$in" -> set)),
    ).flatten.foldLeft(MongoDBObject.newBuilder) {
      case (acc, c) => acc += c
    }.result()
    val limitOpt = Option(limit).map(_.intValue())

    Future.successful(Either.catchNonFatal(dao.find(q).toVector))
  }

  def findAllReadyToFetch: Future[Vector[Track]] = {
    val q = MongoDBObject(
      "artist" -> s"(?i).+".r,
      "title" -> s"(?i).+".r,
      "status" -> Final.dbRepr
    )
    Future.successful(dao.find(q).toVector)
  }

  def genericSearch(text: String, page: Int): Future[ErrorOr[Vector[Track]]] = {
    val q = MongoDBObject("$or" -> List(
      "url" -> s"(?i).*$text.*".r,
      "artist" -> s"(?i).*$text.*".r,
      "title" -> s"(?i).*$text.*".r,
      "status" -> s"(?i).*$text.*".r,
      "playlists" -> s"(?i).*$text.*".r,
    ).map(MongoDBObject(_)))

    Future.successful(Either.catchNonFatal(dao.find(q).slice((page - 1) * 10, page * 10).toVector))
  }
}

