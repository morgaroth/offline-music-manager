package io.morgaroth.media.library.storage

import cats.data.EitherT
import cats.instances.future.catsStdInstancesForFuture
import cats.syntax.either._
import com.mongodb.ConnectionString
import org.mongodb.scala._
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.UpdateOptions
import org.mongodb.scala.result.UpdateResult

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}

trait MongoDBError

case class MongoError(err: Throwable) extends MongoDBError

case class TooManyResultsWhilstSingleWanted(allResults: Seq[Document]) extends MongoDBError

case object SingleObservableNotNexted extends MongoDBError


class MyMongoConnector(connectionString: String) {

  val cs = new ConnectionString(connectionString)

  val mongoClient: MongoClient = MongoClient(connectionString)

  val database: MongoDatabase = mongoClient.getDatabase(cs.getDatabase)

  def getCollection[DocType](collectionName: String): MongoCollection[DocType] = database.getCollection[DocType](collectionName)

  def close(): Unit = mongoClient.close()
}


class MyMongoCollection(connection: MyMongoConnector, collection: String) {

  private val db = connection.getCollection[Document](collection)


  private def wrap[T](obs: SingleObservable[T]) = {
    val p = Promise[Either[MongoDBError, T]]
    obs.subscribe(new Observer[T] {
      var resultCache: Option[T] = None

      override def onNext(result: T) {
        resultCache = Some(result)
      }

      override def onError(e: Throwable) {
        p.success(MongoError(e).asLeft)
      }

      override def onComplete() {
        p.success(resultCache.map(_.asRight[MongoDBError]).getOrElse(SingleObservableNotNexted.asLeft))
      }
    })
    EitherT(p.future)

  }

  def find(query: Bson): EitherT[Future, MongoDBError, Vector[Document]] = {
    wrap(db.find(query).collect()).map(_.toVector)
  }

  def findOne(query: Bson): EitherT[Future, MongoDBError, Option[Document]] = {
    wrap(db.find(query).collect()).subflatMap(data => Either.cond(data.length <= 1, data.headOption, TooManyResultsWhilstSingleWanted(data)))
  }

  def insert(doc: Document): EitherT[Future, MongoDBError, Completed] = {
    wrap(db.insertOne(doc))
  }

  def updateOne(filter: Bson, update: Bson): EitherT[Future, MongoDBError, UpdateResult] = {
    updateOne(filter, update, UpdateOptions())
  }

  def updateOne(filter: Bson, update: Bson, upsert: Boolean): EitherT[Future, MongoDBError, UpdateResult] = {
    wrap(db.updateOne(filter, update, UpdateOptions().upsert(upsert)))
  }

  def updateOne(filter: Bson, update: Bson, updateOptions: UpdateOptions): EitherT[Future, MongoDBError, UpdateResult] = {
    wrap(db.updateOne(filter, update, updateOptions))
  }
}
