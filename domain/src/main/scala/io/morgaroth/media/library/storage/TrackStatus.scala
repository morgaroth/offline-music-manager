package io.morgaroth.media.library.storage

sealed trait TrackStatus extends Product with Serializable {
  lazy val dbRepr: String = getClass.getSimpleName.toLowerCase.stripSuffix("$")
}

object TrackStatus {
  val all = Vector(Draft, Final, Deleted)
  val byDbRepr = all.map(x => x.dbRepr -> x).toMap
  val valuesClasses = all.map(_.getClass)
}

case object Draft extends TrackStatus

case object Final extends TrackStatus

case object Deleted extends TrackStatus