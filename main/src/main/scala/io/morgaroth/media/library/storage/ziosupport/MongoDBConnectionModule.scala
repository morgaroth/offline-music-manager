package io.morgaroth.media.library.storage.ziosupport

import org.bson.UuidRepresentation
import org.bson.codecs.configuration.CodecRegistry
import org.mongodb.scala.{ConnectionString, MongoClient, MongoClientSettings, MongoDatabase, WriteConcern}
import zio._

case class MongoConnectionUrl(uri: String)

case class MongoConnectionCollection(collectionName: String)

case class MongoConnectionConfig(uri: String, collectionName: String)

case class ConnectionInfo(database: MongoDatabase, config: MongoConnectionConfig)

object MongoDBConnectionModule {

  def createConnectionPool(uri: MongoConnectionConfig, codec: CodecRegistry): Task[(MongoClient, ConnectionInfo)] =
    ZIO.effect {
      val mongoUri = new ConnectionString(uri.uri)
      val clientSettings = MongoClientSettings
        .builder()
        .applyConnectionString(mongoUri)
        .uuidRepresentation(UuidRepresentation.STANDARD)
        .codecRegistry(codec)
        .writeConcern(WriteConcern.ACKNOWLEDGED)
        .build()
      val client = MongoClient(clientSettings)
      val database = client.getDatabase(mongoUri.getDatabase)
      client -> ConnectionInfo(database, uri)
    }

  val closeConnectionPool: ((MongoClient, ConnectionInfo)) => ZIO[Any, Nothing, Unit] =
    (cp: (MongoClient, ConnectionInfo)) => ZIO.effect(cp._1.close()).ignore

  def managedConnectionPool(uri: MongoConnectionConfig, codec: CodecRegistry): ZManaged[Any, Throwable, ConnectionInfo] =
    ZManaged.make(createConnectionPool(uri, codec))(closeConnectionPool).map(_._2)

  def forCodec(codecRegistry: CodecRegistry): ZLayer[Has[MongoConnectionConfig], Throwable, Has[ConnectionInfo]] =
    ZLayer.fromServiceManaged(managedConnectionPool(_, codecRegistry))

}
