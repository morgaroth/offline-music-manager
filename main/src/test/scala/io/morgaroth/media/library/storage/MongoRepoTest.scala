package io.morgaroth.media.library.storage

import org.scalatest.flatspec.AnyFlatSpec

import java.time.{LocalDateTime, ZoneId, ZoneOffset}

class MongoRepoTest extends AnyFlatSpec {

  "dadsa" should "Dsa" in {
    val st = LocalDateTime.now()
    println(st)
    println(st.atOffset(ZoneOffset.UTC))
    println(st.atZone(ZoneId.systemDefault()))
    println(LocalDateTime.now(ZoneId.of("UTC")))
  }
}
