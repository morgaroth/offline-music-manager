package io.morgaroth.media.library.gui

import javafx.application.Platform
import zio.*

/** Bridges ZIO effects to the JavaFX application thread.
  *
  * Pattern: run a ZIO effect on ZIO fibers, then push the result
  * to the JavaFX thread via Platform.runLater for UI updates.
  */
object FxBridge:

  /** Run a Task effect off the FX thread and deliver the result back on it.
    * Call this from the JavaFX thread — it forks the work onto ZIO fibers.
    */
  def run[A](runtime: Runtime[Any])(effect: Task[A])(onSuccess: A => Unit, onError: Throwable => Unit = _.printStackTrace()): Unit =
    Unsafe.unsafe { implicit u =>
      runtime.unsafe.run(
        effect.foldZIO(
          err => ZIO.succeed(Platform.runLater(() => onError(err))),
          result => ZIO.succeed(Platform.runLater(() => onSuccess(result))),
        ).forkDaemon
      )
    }
