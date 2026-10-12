package io.morgaroth.media.library.storage

import com.typesafe.scalalogging.LazyLogging
import zio.*

import java.sql.{Connection, ResultSet, Timestamp}
import java.time.ZoneOffset
import java.util.UUID
import javax.sql.DataSource

class FetchQueuePostgres(ds: DataSource) extends FetchQueueService with LazyLogging:

  private def withConnection[A](f: Connection => A): Task[A] =
    ZIO.attemptBlocking:
      val conn = ds.getConnection
      try f(conn)
      finally conn.close()

  private def readItem(rs: ResultSet): FetchQueueItem =
    FetchQueueItem(
      id = UUID.fromString(rs.getString("id")),
      trackId = UUID.fromString(rs.getString("track_id")),
      status = FetchStatus.byDbRepr.getOrElse(rs.getString("status"), FetchStatus.Pending),
      priority = rs.getInt("priority"),
      startedAt = Option(rs.getTimestamp("started_at")).map(_.toInstant.atZone(ZoneOffset.UTC)),
      error = Option(rs.getString("error")),
      createdAt = rs.getTimestamp("created_at").toInstant.atZone(ZoneOffset.UTC),
      title = Option(rs.getString("title")).getOrElse(""),
      artist = Option(rs.getString("artist")).getOrElse(""),
      mediaType = Option(rs.getString("type")).getOrElse("music"),
    )

  override def enqueue(trackIds: Vector[UUID], priority: Int): Task[Int] = withConnection: conn =>
    // Idempotent per track. If already queued, raise its priority to the greater
    // of old/new (so "Fetch now" bumps a track already enqueued by the batch).
    val sql =
      """INSERT INTO fetch_queue (track_id, status, priority) VALUES (?, 'pending', ?)
        |ON CONFLICT (track_id) DO UPDATE SET priority = GREATEST(fetch_queue.priority, EXCLUDED.priority)""".stripMargin
    val ps = conn.prepareStatement(sql)
    var added = 0
    trackIds.foreach: tid =>
      ps.setObject(1, tid)
      ps.setInt(2, priority)
      added += ps.executeUpdate()
    added

  override def claimPending(n: Int): Task[Vector[(UUID, UUID)]] = withConnection: conn =>
    // Atomic claim: lock up to n pending rows, flip to running, return them.
    // FOR UPDATE SKIP LOCKED makes this safe even if called concurrently.
    val sql =
      """UPDATE fetch_queue SET status = 'running', started_at = now()
        |WHERE id IN (
        |  SELECT id FROM fetch_queue WHERE status = 'pending'
        |  ORDER BY priority DESC, created_at FOR UPDATE SKIP LOCKED LIMIT ?
        |)
        |RETURNING id, track_id""".stripMargin
    val ps = conn.prepareStatement(sql)
    ps.setInt(1, n)
    val rs = ps.executeQuery()
    val buf = Vector.newBuilder[(UUID, UUID)]
    while rs.next() do
      buf += (UUID.fromString(rs.getString("id")) -> UUID.fromString(rs.getString("track_id")))
    buf.result()

  override def countRunning: Task[Int] = withConnection: conn =>
    val rs = conn.prepareStatement("SELECT count(*) FROM fetch_queue WHERE status = 'running'").executeQuery()
    rs.next(); rs.getInt(1)

  override def markDone(queueId: UUID): Task[Unit] = withConnection: conn =>
    val ps = conn.prepareStatement("UPDATE fetch_queue SET status = 'done', error = NULL WHERE id = ?")
    ps.setObject(1, queueId)
    ps.executeUpdate()

  override def markErrored(queueId: UUID, error: String): Task[Unit] = withConnection: conn =>
    val ps = conn.prepareStatement("UPDATE fetch_queue SET status = 'errored', error = ? WHERE id = ?")
    ps.setString(1, error.take(2000))
    ps.setObject(2, queueId)
    ps.executeUpdate()

  override def stats: Task[FetchQueueStats] = withConnection: conn =>
    val rs = conn.prepareStatement("SELECT status, count(*) FROM fetch_queue GROUP BY status").executeQuery()
    var p, r, d, e = 0
    while rs.next() do
      val c = rs.getInt(2)
      FetchStatus.byDbRepr.get(rs.getString(1)) match
        case Some(FetchStatus.Pending) => p = c
        case Some(FetchStatus.Running) => r = c
        case Some(FetchStatus.Done)    => d = c
        case Some(FetchStatus.Errored) => e = c
        case None => ()
    FetchQueueStats(p, r, d, e)

  override def list: Task[Vector[FetchQueueItem]] = withConnection: conn =>
    val sql =
      """SELECT q.id, q.track_id, q.status, q.priority, q.started_at, q.error, q.created_at,
        |       t.title, t.artist, t.type
        |FROM fetch_queue q JOIN tracks t ON t.id = q.track_id
        |ORDER BY q.priority DESC, q.created_at""".stripMargin
    val rs = conn.prepareStatement(sql).executeQuery()
    val buf = Vector.newBuilder[FetchQueueItem]
    while rs.next() do buf += readItem(rs)
    buf.result()

  private def deleteWhereStatus(conn: Connection, status: String): Int =
    val ps = conn.prepareStatement("DELETE FROM fetch_queue WHERE status = ?")
    ps.setString(1, status)
    ps.executeUpdate()

  override def clearDone: Task[Int] = withConnection(deleteWhereStatus(_, "done"))
  override def clearPending: Task[Int] = withConnection(deleteWhereStatus(_, "pending"))
  override def clearErrored: Task[Int] = withConnection(deleteWhereStatus(_, "errored"))

  override def resetRunningToPending: Task[Int] = withConnection: conn =>
    conn.prepareStatement("UPDATE fetch_queue SET status = 'pending', started_at = NULL WHERE status = 'running'")
      .executeUpdate()

object FetchQueuePostgres:
  val layer: ZLayer[DataSource, Nothing, FetchQueueService] =
    ZLayer.fromFunction(FetchQueuePostgres(_))
