package io.morgaroth.media.library.http

import io.circe.*
import io.circe.syntax.*
import io.morgaroth.media.library.storage.{Track, TrackStatus}

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/** circe codecs for the domain types exposed over HTTP.
  *
  * circe is already a `domain` dependency, so we reuse it rather than pulling in
  * a second JSON stack. These codecs are intentionally hand-written (not
  * derived) because `Track` has a bespoke shape: several `Option` fields, a
  * `Set[String]`, an enum, and two `ZonedDateTime`s.
  */
object JsonCodecs:

  private val isoFmt: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

  given Encoder[ZonedDateTime] = Encoder.encodeString.contramap(_.format(isoFmt))
  given Decoder[ZonedDateTime] = Decoder.decodeString.emap: s =>
    try Right(ZonedDateTime.parse(s, isoFmt))
    catch case e: Throwable => Left(s"invalid datetime '$s': ${e.getMessage}")

  given Encoder[UUID] = Encoder.encodeString.contramap(_.toString)
  given Decoder[UUID] = Decoder.decodeString.emap: s =>
    try Right(UUID.fromString(s))
    catch case _: Throwable => Left(s"invalid UUID '$s'")

  given Encoder[TrackStatus] = Encoder.encodeString.contramap(_.dbRepr)
  given Decoder[TrackStatus] = Decoder.decodeString.emap: s =>
    TrackStatus.byDbRepr.get(s.toLowerCase).toRight(s"unknown status '$s'")

  given Encoder[Track] = (t: Track) =>
    Json.obj(
      "id" -> t._id.asJson,
      "url" -> t.url.asJson,
      "title" -> t.title.asJson,
      "artist" -> t.artist.asJson,
      "album" -> t.album.asJson,
      "startAt" -> t.startAt.asJson,
      "endAt" -> t.endAt.asJson,
      "fadeOutSeconds" -> t.fadeOutSeconds.asJson,
      "volumeChange" -> t.volumeChange.asJson,
      "status" -> t.status.asJson,
      "idCheck" -> t.idCheck.asJson,
      "playlists" -> t.playlists.asJson,
      "rawTitle" -> t.rawTitle.asJson,
      "rawDescription" -> t.rawDescription.asJson,
      "info" -> t.info.asJson,
      "searchUrl" -> t.searchUrl.asJson,
      "isReadyToFetch" -> t.isReadyToFetch.asJson,
      "updatedAt" -> t.updatedAt.asJson,
      "createdAt" -> t.createdAt.asJson,
    )
