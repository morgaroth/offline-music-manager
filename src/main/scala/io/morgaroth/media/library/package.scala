package io.morgaroth.media

package object library {
  type ErrorOr[T] = Either[Throwable, T]
}
