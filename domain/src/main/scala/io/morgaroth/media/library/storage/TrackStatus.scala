package io.morgaroth.media.library.storage

enum TrackStatus:
  case Draft, Final, Deleted

  lazy val dbRepr: String = this.toString.toLowerCase

object TrackStatus:
  val all: Vector[TrackStatus] = Vector(TrackStatus.Draft, TrackStatus.Final, TrackStatus.Deleted)
  val byDbRepr: Map[String, TrackStatus] = all.map(x => x.dbRepr -> x).toMap
