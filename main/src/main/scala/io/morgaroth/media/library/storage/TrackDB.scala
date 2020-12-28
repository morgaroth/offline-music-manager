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

object TrackStatusCodecProvider extends CodecProvider {
  override def get[T](clazz: Class[T], registry: CodecRegistry): Codec[T] = {
    if (TrackStatus.valuesClasses.contains(clazz)) MongoCodec.TrackStatusCodec.asInstanceOf[Codec[T]] else null
  }
}

object MongoCodec {
  def string[A](decoder: String => A, encoder: A => String)(implicit ct: ClassTag[A]): Codec[A] = new Codec[A] {
    override def decode(reader: BsonReader, decoderContext: DecoderContext) = {
      reader.getCurrentBsonType match {
        case BsonType.STRING => decoder(reader.readString())
        case t => throw new CodecConfigurationException(s"Could not decode ${ct.runtimeClass.getCanonicalName}, expected STRING BsonType but got '$t'.")
      }
    }

    override def encode(writer: BsonWriter, value: A, encoderContext: EncoderContext) = writer.writeString(encoder(value))

    override def getEncoderClass = ct.runtimeClass.asInstanceOf[Class[A]]
  }

  val TrackStatusCodec = string[TrackStatus](TrackStatus.byDbRepr, _.dbRepr)

  val StringUUIDCodec = string[UUID](UUID.fromString, _.toString)
}

object UTCZonedDateTimeMongoCodec extends Codec[ZonedDateTime] {
  override def decode(reader: BsonReader, decoderContext: DecoderContext): ZonedDateTime = {
    reader.getCurrentBsonType match {
      case BsonType.DATE_TIME =>
        Instant.ofEpochMilli(reader.readDateTime).atZone(ZoneOffset.UTC)
      case t =>
        throw new CodecConfigurationException(s"Could not decode into ZonedDateTime, expected DATE_TIME BsonType but got '$t'.")
    }
  }

  override def encode(writer: BsonWriter, value: ZonedDateTime, encoderContext: EncoderContext): Unit = {
    try {
      writer.writeDateTime(value.withZoneSameInstant(ZoneOffset.UTC).toInstant.toEpochMilli)
    } catch {
      case e: ArithmeticException =>
        throw new CodecConfigurationException(s"Unsupported LocalDateTime value '$value' could not be converted to milliseconds: ${e.getMessage}", e)
    }
  }

  override def getEncoderClass: Class[ZonedDateTime] = classOf[ZonedDateTime]
}

object MongoRepo {
  def fromArgs(): TracksDB = {
    val c = ConfigFactory.load().getString("music-library.collection")
    c match {
      case "zbysio" => ZbysioTracks()
      case "christmas" => ChristmasTracks()
      case "all" | "" => AllTracks()
      case other => createDb(other)
    }
  }

  val customCodecs = CodecRegistries.fromCodecs(UTCZonedDateTimeMongoCodec, MongoCodec.StringUUIDCodec, MongoCodec.TrackStatusCodec)
  val customCodecProviders = CodecRegistries.fromProviders(TrackStatusCodecProvider)
  val initialCodecs = CodecRegistries.fromRegistries(customCodecs, customCodecProviders, MongoClient.DEFAULT_CODEC_REGISTRY)

  private val TrackCodec = Macros.createCodecProviderIgnoreNone[Track]

  private def createCollection[K, V: ClassTag](uri: String, collection: String, codecs: CodecProvider) = {
    val finalCodec = CodecRegistries.fromRegistries(
      initialCodecs,
      CodecRegistries.fromProviders(codecs),
    )

    val mongoUri = new ConnectionString(uri)
    val clientSettings = MongoClientSettings.builder().uuidRepresentation(UuidRepresentation.JAVA_LEGACY).applyConnectionString(mongoUri).codecRegistry(finalCodec).build()
    val mongoClient = MongoClient(clientSettings)
    val database = mongoClient
      .getDatabase(mongoUri.getDatabase)

    database.getCollection[V](collection) -> mongoClient
  }

  private def createDB(uri: String, collection: String): TracksDB = {
    import scala.concurrent.ExecutionContext.Implicits.global
    val tuple = createCollection[UUID, Track](uri, collection, TrackCodec)
    val db = new TracksDB(tuple._1, tuple._2)
    db.initialize()
    db
  }

  def createDb(collection: String, uri: String = ConfigFactory.load().getString("music-library.mongo.uri")) = {
    createDB(uri, collection)
  }

  def AllTracks() = createDb("Tracks")

  def ChristmasTracks() = createDb("ChristmasTracks")

  def ZbysioTracks() = createDb("ZbysioTracks")
}

object Fields {
  val status = "status"
  val artist = "artist"
  val updatedAt = "updatedAt"
  val title = "title"
  val _rawId = "_id"
}

class TracksDB(val db: MongoCollection[Track], client: MongoClient)(implicit ex: ExecutionContext) extends TracksStorage[Future] with LazyLogging {
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

  def updatePlaylists(id: UUID, newData: Set[String]): Future[ErrorOr[Track]] = {
    getById(id)
      .flatMapE(_ => updateFields(id, Updates.set("playlists", newData)))
      .flatMapE(_ => getById(id))
  }

  def updateRawTitle(id: UUID, newData: Option[String]): Future[ErrorOr[Track]] = {
    getById(id)
      .flatMapE(_ => updateFields(id, condSet("rawTitle", newData)))
      .flatMapE(_ => getById(id))
  }

  def updateRawDescription(id: UUID, newData: Option[String]): Future[ErrorOr[Track]] = {
    getById(id)
      .flatMapE(_ => updateFields(id, condSet("rawDescription", newData)))
      .flatMapE(_ => getById(id))
  }

  def findAllPlaylists(): Future[ErrorOr[Map[String, Vector[Track]]]] = {
    db.find(Filters.exists("playlists.0"))
      .toFuture()
      .map(_.toVector.flatMap(x => x.playlists.getOrElse(Set.empty).map(_ -> x)).groupBy(_._1).mapValues(_.map(_._2)))
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
    val q = Filters.or(
      Filters.regex("url", s"(?i).*$text.*"),
      Filters.regex(Fields.artist, s"(?i).*$text.*"),
      Filters.regex(Fields.title, s"(?i).*$text.*"),
      Filters.regex(Fields.status, s"(?i).*$text.*"),
      Filters.regex("playlists", s"(?i).*$text.*"),
    )

    db.find(q).skip((page - 1) * 10).limit(page).toFuture().map(_.toVector).toEither
  }

  def close(): Unit = client.close()
}

