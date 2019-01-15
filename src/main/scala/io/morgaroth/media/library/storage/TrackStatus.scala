package io.morgaroth.media.library.storage

sealed trait TrackStatus {
  lazy val dbRepr: String = getClass.getSimpleName.toLowerCase.stripSuffix("$")
}

object TrackStatus {
  val all = Vector(Draft, Final)
  val byDbRepr = all.map(x => x.dbRepr -> x).toMap
}

case object Draft extends TrackStatus

case object Final extends TrackStatus