package io.morgaroth.media

import java.security.MessageDigest
import java.math.BigInteger

package object library:
  type ErrorOr[T] = Either[Throwable, T]

  def md5HashString(s: String): String =
    val md = MessageDigest.getInstance("MD5")
    val digest = md.digest(s.getBytes)
    val bigInt = BigInteger(1, digest)
    bigInt.toString(16)
