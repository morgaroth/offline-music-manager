package io.morgaroth.media.library.http

import io.morgaroth.media.library.Configuration
import io.morgaroth.media.library.jobs.ZIOFetcher
import io.morgaroth.media.library.storage.{FetchQueueService, Track, ZioTracksStorageService}
import zio.*

import java.util.UUID

/** Background orchestrator for the durable fetch queue.
  *
  * A single daemon fiber polls the queue: while there are pending rows and fewer
  * than `maxParallelism` running, it claims the next pending rows (atomically,
  * via SKIP LOCKED), forks each fetch, and marks the row done/errored. When no
  * pending/running rows remain it clears the done rows (table empties). Errored
  * rows are never auto-removed.
  *
  * "Only one job runs" is enforced by the queue itself: the queue is the job.
  */
class FetchOrchestrator(
  queue: FetchQueueService,
  storage: ZioTracksStorageService,
  fetcher: ZIOFetcher,
  serverConfig: ServerConfig,
  maxParallelism: Int,
):

  private def configFor: Configuration =
    Configuration(
      action = "fetch",
      destinationDir = serverConfig.outputDir,
      videoDestinationDir = serverConfig.videoOutputDir,
      cacheDir = serverConfig.cacheDir,
      downloaderExec = serverConfig.downloaderExec,
      cookiesFile = serverConfig.cookiesFile,
    )

  /** Run one claimed item to completion, updating its queue row. */
  private def runItem(queueId: UUID, trackId: UUID): UIO[Unit] =
    val work =
      for
        track <- storage.getById(trackId)
        given Track = track
        result <- fetcher.handleUrl(configFor)
        _ <- ZIO.fromEither(result) // fetch-level Left -> errored
      yield ()
    work.foldZIO(
      failure = t => queue.markErrored(queueId, s"${t.getClass.getSimpleName}: ${t.getMessage}").orDie,
      success = _ => queue.markDone(queueId).orDie,
    )

  /** One scheduling tick: top up running fibers from pending, then clean up. */
  private def tick: UIO[Unit] =
    (for
      running <- queue.countRunning
      free = math.max(0, maxParallelism - running)
      _ <- ZIO.when(free > 0):
        queue.claimPending(free).flatMap: claimed =>
          // Fork each claimed item; the row is already 'running'.
          ZIO.foreachDiscard(claimed): (qid, tid) =>
            runItem(qid, tid).forkDaemon
      // Cleanup: when nothing is pending or running, clear the done rows.
      stats <- queue.stats
      _ <- ZIO.when(stats.pending == 0 && stats.running == 0 && stats.done > 0):
        queue.clearDone.unit
    yield ()).catchAllCause(c => ZIO.logErrorCause("fetch orchestrator tick failed", c))

  /** The daemon loop: tick every few seconds, forever. */
  val run: UIO[Unit] =
    (tick *> ZIO.sleep(3.seconds)).forever

object FetchOrchestrator:
  val live: ZLayer[FetchQueueService & ZioTracksStorageService & ZIOFetcher & ServerConfig, Nothing, FetchOrchestrator] =
    ZLayer:
      for
        queue <- ZIO.service[FetchQueueService]
        storage <- ZIO.service[ZioTracksStorageService]
        fetcher <- ZIO.service[ZIOFetcher]
        cfg <- ZIO.service[ServerConfig]
      yield FetchOrchestrator(queue, storage, fetcher, cfg, cfg.fetchParallelism)
