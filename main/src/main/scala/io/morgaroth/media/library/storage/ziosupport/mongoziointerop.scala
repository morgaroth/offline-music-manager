package io.morgaroth.media.library.storage.ziosupport

import org.mongodb.scala.SingleObservable
import zio.Task

import scala.concurrent.Promise

object mongoziointerop {

  implicit class ToTask[T](a: SingleObservable[T]) {
    def toTask: Task[T] =
      Task.fromFunctionFuture { _ =>
        val prom = Promise[T]
        a.subscribe(x => prom.success(x), x => prom.failure(x))
        prom.future
      }
  }
}
