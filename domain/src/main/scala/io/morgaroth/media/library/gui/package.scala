package io.morgaroth.media.library

import io.morgaroth.media.library.storage.Track

import scala.concurrent.{Await, Future}

package object gui {

  implicit class ds(in: Option[Track]) {
    def orEmpty(extractor: Track => String): String = {
      in.map(extractor).getOrElse("")
    }
  }

  implicit class FutureAwaitable[A](future: Future[A]) {

    import scala.concurrent.duration._

    def await(): A = Await.result(future, 10.seconds)

    def await(tm: FiniteDuration): A = Await.result(future, tm)
  }

}
