package io.morgaroth.media.library

import java.util.UUID

import salat.Context
import salat.transformers.CustomTransformer

package object storage {

  implicit val ctx = new Context {
    override val name = "MJ"

    registerCustomTransformer[TrackStatus, String](new CustomTransformer[TrackStatus, String]() {
      override def deserialize(b: String): TrackStatus = TrackStatus.byDbRepr(b)

      override def serialize(a: TrackStatus): String = a.dbRepr
    })

    registerCustomTransformer[UUID, String](new CustomTransformer[UUID, String]() {
      override def deserialize(b: String): UUID = UUID.fromString(b)

      override def serialize(a: UUID) = a.toString
    })
  }
}
