package io.morgaroth.media.library.http

import io.morgaroth.media.library.Configuration
import io.morgaroth.media.library.jobs.ZIOFetcher
import io.morgaroth.media.library.storage.{Track, ZioTracksStorageService}
import zio.*

import java.time.Instant
import java.util.UUID

/** State of a single asynchronous fetch job. */
enum JobState:
  case Running, Succeeded, Failed

case class JobStatus(
  jobId: UUID,
  trackId: UUID,
  state: JobState,
  startedAt: Instant,
  finishedAt: Option[Instant],
  error: Option[String],
)

/** Tracks fire-and-forget fetch jobs.
  *
  * A fetch (yt-dlp download + ffmpeg convert + tag) runs for minutes, so we do
  * not block the HTTP request. `submit` starts the work on a daemon fiber and
  * returns immediately with a job id; callers poll `get` for status.
  *
  * `ZIOFetcher` already caps real download concurrency internally, so this
  * registry only needs to track lifecycle, not throttle.
  */
class JobRegistry(
  ref: Ref[Map[UUID, JobStatus]],
  storage: ZioTracksStorageService,
  fetcher: ZIOFetcher,
  serverConfig: ServerConfig,
):

  /** Build a single-track fetch configuration. Destination and cache dirs come
    * from `ServerConfig` (env-driven), so the add-on can point output at an
    * NTFS mount; force/debug are overridden per request.
    */
  private def configFor(forceLevel: Int, debug: Boolean): Configuration =
    Configuration(
      action = "fetch",
      destinationDir = serverConfig.outputDir,
      cacheDir = serverConfig.cacheDir,
      downloaderExec = serverConfig.downloaderExec,
      forceLevel = forceLevel,
      debug = debug,
    )

  def submit(trackId: UUID, forceLevel: Int, debug: Boolean): Task[JobStatus] =
    for
      jobId <- ZIO.succeed(UUID.randomUUID())
      now <- Clock.instant
      initial = JobStatus(jobId, trackId, JobState.Running, now, None, None)
      _ <- ref.update(_ + (jobId -> initial))
      cfg = configFor(forceLevel, debug)
      work = runFetch(trackId, cfg).foldZIO(
        failure = t => complete(jobId, JobState.Failed, Some(t.getMessage)),
        success = _ => complete(jobId, JobState.Succeeded, None),
      )
      _ <- work.forkDaemon
    yield initial

  private def runFetch(trackId: UUID, cfg: Configuration): Task[Unit] =
    for
      track <- storage.getById(trackId)
      given Track = track
      result <- fetcher.handleUrl(cfg)
      _ <- ZIO.fromEither(result) // surface fetch errors as job failure
    yield ()

  private def complete(jobId: UUID, state: JobState, error: Option[String]): UIO[Unit] =
    Clock.instant.flatMap: now =>
      ref.update: jobs =>
        jobs.get(jobId) match
          case Some(status) =>
            jobs + (jobId -> status.copy(state = state, finishedAt = Some(now), error = error))
          case None => jobs

  def get(jobId: UUID): UIO[Option[JobStatus]] =
    ref.get.map(_.get(jobId))

  def list: UIO[Vector[JobStatus]] =
    ref.get.map(_.values.toVector.sortBy(_.startedAt).reverse)

object JobRegistry:
  val live: ZLayer[ZioTracksStorageService & ZIOFetcher & ServerConfig, Nothing, JobRegistry] =
    ZLayer:
      for
        ref <- Ref.make(Map.empty[UUID, JobStatus])
        storage <- ZIO.service[ZioTracksStorageService]
        fetcher <- ZIO.service[ZIOFetcher]
        serverConfig <- ZIO.service[ServerConfig]
      yield JobRegistry(ref, storage, fetcher, serverConfig)
