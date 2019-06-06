package io.morgaroth.media.library.storage

import java.time.LocalDateTime
import java.util.UUID

import cats.data.EitherT
import cats.implicits._
import cats.instances.future.catsStdInstancesForFuture
import com.typesafe.scalalogging.LazyLogging
import io.morgaroth.media.library.ErrorOr
import org.mongodb.scala.bson.BsonString
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.bson.conversions.Bson

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait TrackDbError

case class TrackNotFound(desc: String) extends Exception(s"track not found $desc") with TrackDbError

case class MongoRawError(err: MongoDBError) extends TrackDbError

case class Track(
                  url: String,
                  title: String,
                  artist: String,
                  startAt: Option[String],
                  endAt: Option[String],
                  fadeOutSeconds: Option[Int],
                  volumeChange: Option[BigDecimal],
                  status: TrackStatus,
                  idCheck: Option[String],
                  playlists: Set[String],
                  updatedAt: LocalDateTime = LocalDateTime.now(),
                  createdAt: LocalDateTime = LocalDateTime.now(),
                  id: UUID = UUID.randomUUID(),
                ) {
  lazy val info = s"$artist - $title"
  lazy val UFID: String = io.morgaroth.media.library.md5HashString(s"$url$title$artist$startAt$endAt$fadeOutSeconds:$volumeChange")
}

object Track {
  def apply(
             url: String,
             title: String,
             artist: String,
             status: TrackStatus,
           ): Track = {
    new Track(url, title, artist, none, none, none, none, status, TrackId(artist, title), Set.empty)
  }

  def apply(
             url: String,
             title: String,
             artist: String,
             status: TrackStatus,
             startAt: Option[String],
             endAt: Option[String],
             fadeOutSeconds: Option[Int],
             volumeChange: Option[BigDecimal],
             playlists: Set[String],
           ): Track = {
    new Track(url, title, artist, startAt, endAt, fadeOutSeconds, volumeChange, status, TrackId(artist, title), playlists)
  }

  def apply(url: String): Track = {
    new Track(url, "", "", None, None, None, None, Draft, None, Set.empty)
  }
}

object TrackId {
  val sep = "___"

  def apply(artist: String, title: String): Option[String] =
    if (title.nonEmpty || artist.nonEmpty) s"$artist$sep$title".some else none
}

class TracksDB(val dao: MyMongoCollection) extends LazyLogging {

  //  private val dao = new MongoDAOJodaSupport[Track](connectionCfg, "Tracks")
  //  dao.collection.createIndex("id")
  //  dao.collection.createIndex(
  //    MongoDBObject("idCheck" -> 1),
  //    MongoDBObject(
  //      "partialFilterExpression" -> MongoDBObject("idCheck" -> MongoDBObject("$exists" -> true)),
  //      "unique" -> true,
  //    )
  //  )
  //
  //  UUIDConversionHelpers.register()

  def extract(doc: Document) = {
    Track(
      url = doc.getString("url"),
      title = doc.getString("title"),
      artist = doc.getString("artist"),
      startAt = doc.getString("startAt"),
    )
  }

  def all: EitherT[Future, MongoDBError, Vector[Document]] = dao.find(Document())

  def getById(id: UUID): EitherT[Future, TrackDbError, Document] =
    dao
      .findOne(Document("_id" -> BsonString(id.toString)))
      .leftMap(MongoRawError)
      .subflatMap(_.map(_.asRight).getOrElse(TrackNotFound(s"by id $id").asLeft))

  //  def getBy(artist: String, title: String): ErrorOr[Track] =
  //    Either.catchNonFatal(dao.findOne(MongoDBObject("artist" -> artist, "title" -> title))).flatMap(
  //      _.map(_.asRight).getOrElse(TrackNotFound(s"by artist/title $artist/$title").asLeft)
  //    )

  def save(document: Track): ErrorOr[Track] = {
    Either.catchNonFatal(dao.insert(document)).flatMap(_ => getById(document.id))
  }

  def store(url: String): ErrorOr[Track] = save(Track(url))

  private def updateFields(id: UUID, kv: Bson, kvRest: Bson*) = {
    import org.mongodb.scala.model.Updates._

    val a = combine(kv +: kvRest: _*)

    Either.catchNonFatal(dao.updateOne(
      Document("_id" -> BsonString(id.toString)),
      a,
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

  def updateVolumeChange(id: UUID, newData: Option[BigDecimal]): ErrorOr[Track] = {
    updateFields(id, "volumeChange" -> newData.map(_.toDouble)) >> getById(id)
  }

  def updatePlaylists(id: UUID, newData: Set[String]): ErrorOr[Track] = {
    updateFields(id, "playlists" -> newData) >> getById(id)
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

    Either.catchNonFatal(limitOpt.map(dao.find(q).take(_)).getOrElse(dao.find(q)).toVector)
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

