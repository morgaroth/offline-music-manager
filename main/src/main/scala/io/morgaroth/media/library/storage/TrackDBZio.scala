package io.morgaroth.media.library.storage

import cats.syntax.either._
import com.mongodb.ConnectionString
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import io.morgaroth.media.library.ErrorOr
import org.bson.codecs.configuration.{CodecConfigurationException, CodecProvider, CodecRegistries, CodecRegistry}
import org.bson.codecs.{Codec, DecoderContext, EncoderContext}
import org.bson.{BsonReader, BsonType, BsonWriter, UuidRepresentation}
import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.bson.codecs.Macros
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.{Filters, FindOneAndUpdateOptions, IndexOptions, Updates}
import org.mongodb.scala.{MongoClient, MongoClientSettings, MongoCollection}

import java.time.{Instant, ZoneOffset, ZonedDateTime}
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag


class TracksDBZio(val db: MongoCollection[Track], client: MongoClient)(implicit ex: ExecutionContext) extends TracksStorage[Future] with LazyLogging {
  def initialize() = {
    db.createIndex(BsonDocument("id" -> 1))

    val options = IndexOptions()
      .partialFilterExpression(Filters.exists("idCheck"))
      .unique(true)

    db.createIndex(
      BsonDocument("idCheck" -> 1),
      options
    )
  }

  def all: Future[Vector[Track]] = db.find().toFuture().map(_.toVector)

  implicit class ErrorOrT[A](f: Future[ErrorOr[A]]) {
    def flatMapE[B](afe: A => Future[ErrorOr[B]]): Future[ErrorOr[B]] = {
      f.flatMap(_.fold(x => Future.successful(x.asLeft), x => afe(x)))
    }

    def semiFlatMapE[B](afe: A => ErrorOr[B]): Future[ErrorOr[B]] = {
      f.flatMap(_.fold(x => Future.successful(x.asLeft), x => Future.successful(afe(x))))
    }
  }

  implicit class RecoverToEither[A](f: Future[A]) {
    def toEither: Future[ErrorOr[A]] = f.map(_.asRight).recover { case e: Throwable => e.asLeft }
  }

  def getById(id: UUID): Future[ErrorOr[Track]] =
    db.find(equal(Fields._rawId, id.toString)).first().headOption()
      .map(Either.fromOption(_, TrackNotFound(s"by id $id")))
      .recover { case e: Throwable => e.asLeft }

  def save(document: Track): Future[ErrorOr[Track]] = {
    db.insertOne(document).toFuture().toEither.flatMapE { _ => getById(document._id) }
  }

  private def updateFields(id: UUID, update: Bson, updates: Bson*) = {
    val q = Updates.combine(updates.toVector ++ Vector(update, Updates.set(Fields.updatedAt, ZonedDateTime.now())): _*)
    val opts = FindOneAndUpdateOptions()
    opts.upsert(false)
    db.findOneAndUpdate(equal(Fields._rawId, id), q, opts).toFuture().toEither.semiFlatMapE { x =>
      if (x == null) {
        TrackNotFound(s"by id $id").asLeft
      } else {
        x.asRight
      }
    }
  }


  def updateArtist(id: UUID, artist: String): Future[ErrorOr[Track]] = {
    getById(id)
      .flatMapE(db => updateFields(id, Updates.set(artist, artist), Updates.set("idCheck", TrackId(artist, db.title).get)))
      .flatMapE(_ => getById(id))
  }


  def updateTitle(id: UUID, title: String): Future[ErrorOr[Track]] = {
    getById(id)
      .flatMapE(db => updateFields(id, Updates.set(Fields.title, title), Updates.set("idCheck", TrackId(db.artist, title).get)))
      .flatMapE(_ => getById(id))
  }

  def updateAlbum(id: UUID, album: String): Future[ErrorOr[Track]] = {
    getById(id)
      .flatMapE(_ => updateFields(id, Updates.set("album", album)))
      .flatMapE(_ => getById(id))
  }

  def condSet[A](name: String, value: Option[A]) = {
    value.fold(Updates.unset(name))(Updates.set(name, _))
  }

  def updateStartAt(id: UUID, data: Option[String]): Future[ErrorOr[Track]] = {
    getById(id)
      .flatMapE(_ => updateFields(id, condSet("startAt", data)))
      .flatMapE(_ => getById(id))
  }

  def updateEndAt(id: UUID, data: Option[String]): Future[ErrorOr[Track]] = {
    getById(id)
      .flatMapE(_ => updateFields(id, condSet("endAt", data)))
      .flatMapE(_ => getById(id))
  }

  def updateUrl(id: UUID, url: String): Future[ErrorOr[Track]] = {
    getById(id)
      .flatMapE(_ => updateFields(id, Updates.set("url", url), Updates.set(Fields.status, Draft.dbRepr)))
      .flatMapE(_ => getById(id))
  }

  def updateStatus(id: UUID, status: TrackStatus): Future[ErrorOr[Track]] = {
    getById(id)
      .flatMapE(_ => updateFields(id, Updates.set(Fields.status, status.dbRepr)))
      .flatMapE(_ => getById(id))
  }

  def updateFadeOutSeconds(id: UUID, newData: Option[Int]): Future[ErrorOr[Track]] = {
    getById(id)
      .flatMapE(_ => updateFields(id, condSet("fadeOutSeconds", newData)))
      .flatMapE(_ => getById(id))
  }

  def updateVolumeChange(id: UUID, newData: Option[BigDecimal]): Future[ErrorOr[Track]] = {
    getById(id)
      .flatMapE(_ => updateFields(id, condSet("volumeChange", newData)))
      .flatMapE(_ => getById(id))
  }

  val hardcodedPlaylistsInsteadOfSmartManagement = Set(
    "electronic", "christmas", "all", "zbysio",
  )

  def updatePlaylists(id: UUID, newData: Set[String]): Future[ErrorOr[Track]] = {
    getById(id)
      .semiFlatMapE(_ => Either.cond(newData.intersect(hardcodedPlaylistsInsteadOfSmartManagement).size == newData.size, (), UnknownPlaylist(newData.diff(hardcodedPlaylistsInsteadOfSmartManagement).mkString)))
      .flatMapE(_ => updateFields(id, Updates.set("playlists", newData)))
      .flatMapE(_ => getById(id))
  }

  def updateRawTitle(id: UUID, newData: String): Future[ErrorOr[Track]] = {
    getById(id)
      .flatMapE(_ => updateFields(id, Updates.set("rawTitle", newData)))
      .flatMapE(_ => getById(id))
  }

  def updateRawDescription(id: UUID, newData: String): Future[ErrorOr[Track]] = {
    getById(id)
      .flatMapE(_ => updateFields(id, Updates.set("rawDescription", newData)))
      .flatMapE(_ => getById(id))
  }

  def findAllPlaylists(): Future[ErrorOr[Map[String, Vector[Track]]]] = {
    db.find(Filters.exists("playlists.0"))
      .toFuture()
      .map(_.toVector.flatMap(x => x.playlists.map(_ -> x)).groupBy(_._1).mapValues(_.map(_._2)))
      .toEither
  }

  def search(
              artist: Option[String] = None, title: Option[String] = None,
              statuses: Option[Set[TrackStatus]] = None,
              limit: java.lang.Integer = null
            ): Future[ErrorOr[Vector[Track]]] = {
    val q = Filters.and(List(
      artist.map(str => Filters.regex(Fields.artist, s"(?i).*$str.*")),
      title.map(str => Filters.regex(Fields.title, s"(?i).*$str.*".r)),
      statuses.map(_.map(_.dbRepr)).map(set => Filters.in(Fields.status, set)),
    ).flatten: _*)
    db.find(q).toFuture().map(_.toVector).toEither
  }

  def findAllReadyToFetch: Future[Vector[Track]] = {
    val q = Filters.and(
      Filters.eq(Fields.status, Final.dbRepr),
      Filters.regex(Fields.artist, "(?i).+"),
      Filters.regex(Fields.title, "(?i).+"),
    )
    db.find(q).toFuture().map(_.toVector)
  }

  def genericSearch(text: String, page: Int): Future[ErrorOr[Vector[Track]]] = {
    val filters = Vector(
      Filters.regex("url", s"(?i).*$text.*"),
      Filters.regex(Fields.artist, s"(?i).*$text.*"),
      Filters.regex(Fields.title, s"(?i).*$text.*"),
      Filters.regex(Fields.status, s"(?i).*$text.*"),
      Filters.regex("playlists", s"(?i).*$text.*"),
    )
    val q = if (text.trim.nonEmpty) Filters.or(filters: _*) else BsonDocument()
    db.find(q).skip((page - 1) * 10).limit(10).toFuture().map(_.toVector).toEither
  }

  def close(): Unit = client.close()
}

