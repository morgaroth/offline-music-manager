package io.morgaroth.media.library.gui

import javafx.application.Platform
import zio.*

/** Bridges ZIO effects to the JavaFX application thread.
  *
  * Pattern: run a ZIO effect on ZIO fibers, then push the result
  * to the JavaFX thread via Platform.runLater for UI updates.
  */
object FxBridge:

  /** Run a ZIO effect and deliver the result on the JavaFX thread. */
  def runOnFx[R, A](effect: ZIO[R, Throwable, A])(onSuccess: A => Unit, onError: Throwable => Unit = _.printStackTrace()): ZIO[R, Nothing, Fiber.Runtime[Nothing, Unit]] =
    effect.foldZIO(
      err => ZIO.succeed(Platform.runLater(() => onError(err))),
      result => ZIO.succeed(Platform.runLater(() => onSuccess(result))),
    ).fork

  /** Execute a UI update on the JavaFX application thread from a ZIO context. */
  def onFxThread(action: => Unit): UIO[Unit] =
    ZIO.succeed(Platform.runLater(() => action))

  /** Run a ZIO effect, ignoring errors, delivering result on FX thread. */
  def runOnFxIgnoreErrors[R, A](effect: ZIO[R, Throwable, A])(onSuccess: A => Unit): ZIO[R, Nothing, Fiber.Runtime[Nothing, Unit]] =
    runOnFx(effect)(onSuccess, _ => ())
