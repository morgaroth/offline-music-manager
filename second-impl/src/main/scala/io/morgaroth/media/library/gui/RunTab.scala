package io.morgaroth.media.library.gui

import io.morgaroth.media.library.jobs.ZIOFetcher
import io.morgaroth.media.library.storage.{Track, ZioTracksStorageService}
import io.morgaroth.media.library.Configuration
import javafx.application.Platform
import scalafx.Includes.*
import scalafx.geometry.Insets
import scalafx.scene.control.{Button, Label, ProgressBar, TextArea, TextField}
import scalafx.scene.layout.{HBox, Priority, VBox}
import zio.*

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import scala.sys.process.*

/** Run tab: launch the fetch/download pipeline with 3 concurrent workers.
  * Each worker has its own log panel and progress bar.
  */
class RunTab(backend: GuiBackend, storage: ZioTracksStorageService, runtime: Runtime[Any]) extends VBox:
  spacing = 10
  padding = Insets(10)

  private val destDirField = new TextField:
    text = s"${java.lang.System.getProperty("user.home")}/music-library/all-music"
    hgrow = Priority.Always

  private val downloaderField = new TextField:
    text = "yt-dlp"
    prefWidth = 120

  private val startButton = new Button("Start"):
    prefWidth = 100

  private val stopButton = new Button("Stop"):
    prefWidth = 80
    disable = true

  private val overallProgress = new ProgressBar:
    prefWidth = 300
    progress = 0.0

  private val statusLabel = new Label("Gotowy")

  private var runningFiber: Option[Fiber.Runtime[Any, Any]] = None

  // Three worker panels
  private val workers = (1 to 3).map(i => WorkerPanel(i)).toVector

  startButton.onAction = _ => startFetching()
  stopButton.onAction = _ => stopFetching()

  private val controlBar = new HBox:
    spacing = 8
    children = Seq(
      new Label("Katalog:"), destDirField,
      new Label("Downloader:"), downloaderField,
      startButton, stopButton, overallProgress, statusLabel,
    )

  private val workersBox = new HBox:
    spacing = 8
    children = workers
    workers.foreach(w => HBox.setHgrow(w, Priority.Always))

  children = Seq(controlBar, workersBox)
  VBox.setVgrow(workersBox, Priority.Always)

  private def startFetching(): Unit =
    startButton.disable = true
    stopButton.disable = false
    statusLabel.text = "Pobieram listę..."
    workers.foreach(_.clear())

    val cfg = Configuration(
      action = "fetch",
      destinationDir = File(destDirField.text.value),
      cacheDir = File(File(java.lang.System.getProperty("user.home")), "music-library/cache"),
      downloaderExec = downloaderField.text.value,
    )

    val work = for
      tracks <- storage.findAllReadyToFetch.map(_.sortBy(_.updatedAt).reverse)
      _ <- ZIO.succeed(Platform.runLater { () =>
        statusLabel.text = s"Znaleziono ${tracks.size} do pobrania"
        overallProgress.progress = 0.0
      })
      totalCount = tracks.size
      counter <- Ref.make(0)
      _ <- ZIO.foreachParDiscard(tracks) { track =>
        for
          workerIdx <- ZIO.succeed(track.hashCode().abs % 3)
          _ <- ZIO.succeed(Platform.runLater(() => workers(workerIdx).logLine(s">>> ${track.info}")))
          _ <- ZIO.succeed(Platform.runLater(() => workers(workerIdx).setWorking(track.info)))
          result <- processTrack(track, cfg, workerIdx)
          n <- counter.updateAndGet(_ + 1)
          _ <- ZIO.succeed(Platform.runLater { () =>
            overallProgress.progress = n.toDouble / totalCount
            statusLabel.text = s"$n / $totalCount"
            workers(workerIdx).setIdle()
          })
        yield ()
      }.withParallelism(3)
      _ <- ZIO.succeed(Platform.runLater { () =>
        statusLabel.text = "Gotowe!"
        overallProgress.progress = 1.0
        startButton.disable = false
        stopButton.disable = true
      })
    yield ()

    Unsafe.unsafe { implicit u =>
      val fiber = runtime.unsafe.run(work.forkDaemon).getOrThrowFiberFailure()
      runningFiber = Some(fiber)
    }

  private def stopFetching(): Unit =
    runningFiber.foreach: fiber =>
      Unsafe.unsafe { implicit u =>
        runtime.unsafe.run(fiber.interrupt)
      }
    runningFiber = None
    startButton.disable = false
    stopButton.disable = true
    statusLabel.text = "Zatrzymano"

  private def processTrack(track: Track, cfg: Configuration, workerIdx: Int): Task[Unit] =
    val fetcher = ZIOFetcher(storage)
    given Track = track
    ZIO.blocking(fetcher.handleUrl(cfg)).foldZIO(
      err =>
        ZIO.succeed(Platform.runLater(() =>
          workers(workerIdx).logLine(s"[ERROR] ${track.info}: ${err.getMessage}")
        )),
      _ =>
        ZIO.succeed(Platform.runLater(() =>
          workers(workerIdx).logLine(s"[OK] ${track.info}")
        ))
    )

  /** A panel for a single download worker with log and progress indicator. */
  class WorkerPanel(num: Int) extends VBox:
    spacing = 4
    padding = Insets(5)
    prefWidth = 320

    private val headerLabel = new Label(s"Worker $num"):
      style = "-fx-font-weight: bold"

    private val currentTrackLabel = new Label("Idle"):
      style = "-fx-text-fill: #666"

    private val workerProgress = new ProgressBar:
      prefWidth = 300
      progress = -1.0 // indeterminate when working
      visible = false

    private val logArea = new TextArea:
      editable = false
      wrapText = true
      vgrow = Priority.Always
      style = "-fx-font-family: monospace; -fx-font-size: 11"

    children = Seq(headerLabel, currentTrackLabel, workerProgress, logArea)
    VBox.setVgrow(logArea, Priority.Always)

    def logLine(msg: String): Unit =
      val ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
      logArea.appendText(s"[$ts] $msg\n")

    def setWorking(trackInfo: String): Unit =
      currentTrackLabel.text = trackInfo
      workerProgress.visible = true
      workerProgress.progress = -1.0

    def setIdle(): Unit =
      currentTrackLabel.text = "Idle"
      workerProgress.visible = false

    def clear(): Unit =
      logArea.text = ""
      setIdle()
