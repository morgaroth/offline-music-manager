package io.morgaroth.media.library.storage

import zio.*

import java.time.ZonedDateTime
import java.util.UUID

/** Lifecycle of a queued fetch. */
enum FetchStatus:
  case Pending, Running, Done, Errored

  lazy val dbRepr: String = this.toString.toLowerCase

object FetchStatus:
  val all: Vector[FetchStatus] = Vector(Pending, Running, Done, Errored)
  val byDbRepr: Map[String, FetchStatus] = all.map(s => s.dbRepr -> s).toMap

/** A queued fetch row, joined with the track's human-readable fields. */
case class FetchQueueItem(
  id: UUID,
  trackId: UUID,
  status: FetchStatus,
  startedAt: Option[ZonedDateTime],
  error: Option[String],
  createdAt: ZonedDateTime,
  title: String,
  artist: String,
  mediaType: String,
)

/** Aggregate counts for the queue header. */
case class FetchQueueStats(pending: Int, running: Int, done: Int, errored: Int):
  def total: Int = pending + running + done + errored

/** Durable fetch queue. Backed by the `fetch_queue` table. */
trait FetchQueueService:
  /** Enqueue the given track ids as `pending` (idempotent per track via the
    * unique index — already-queued tracks are skipped). Returns how many new
    * rows were added.
    */
  def enqueue(trackIds: Vector[UUID]): Task[Int]

  /** Atomically claim up to `n` pending rows, marking them running with a start
    * timestamp, and return the claimed (queueId, trackId) pairs.
    */
  def claimPending(n: Int): Task[Vector[(UUID, UUID)]]

  def countRunning: Task[Int]
  def markDone(queueId: UUID): Task[Unit]
  def markErrored(queueId: UUID, error: String): Task[Unit]

  def stats: Task[FetchQueueStats]
  def list: Task[Vector[FetchQueueItem]]

  /** Delete all `done` rows (normal cleanup). Returns rows removed. */
  def clearDone: Task[Int]
  /** Delete all `pending` rows (kill: lets running finish). Returns removed. */
  def clearPending: Task[Int]
  /** Delete all `errored` rows (admin). Returns removed. */
  def clearErrored: Task[Int]

  /** One-time boot recovery: reset stale `running` rows back to `pending`. */
  def resetRunningToPending: Task[Int]

object FetchQueueService:
  def enqueue(ids: Vector[UUID]): ZIO[FetchQueueService, Throwable, Int] =
    ZIO.serviceWithZIO[FetchQueueService](_.enqueue(ids))
