package io.morgaroth.media.library.storage

import io.morgaroth.media.library.ErrorOr

import java.net.URLEncoder
import java.time.{LocalDateTime, ZoneId, ZonedDateTime}
import java.util.UUID

case class TrackNotFound(desc: String) extends Exception(s"track not found $desc")

case class UnknownPlaylist(desc: String) extends Exception(s"playlists not found $desc")

case class Track(
  url: String,
  title: String,
  artist: String,
  album: String,
  startAt: Option[String],
  endAt: Option[String],
  fadeOutSeconds: Option[Int],
  volumeChange: Option[BigDecimal],
  status: TrackStatus,
  idCheck: Option[String],
  playlists: Set[String],
  rawTitle: String,
  rawDescription: String,
  updatedAt: ZonedDateTime = ZonedDateTime.now(),
  createdAt: ZonedDateTime = ZonedDateTime.now(),
  _id: UUID = UUID.randomUUID(),
):
  lazy val searchUrl: String =
    s"https://www.youtube.com/results?search_query=${URLEncoder.encode(s"$title $artist", "utf-8")}"

  lazy val isReadyToFetch: Boolean =
    title.nonEmpty && artist.nonEmpty && status == TrackStatus.Final

  lazy val createdAtLocal: LocalDateTime =
    createdAt.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime

  lazy val updatedAtLocal: LocalDateTime =
    updatedAt.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime

  lazy val info: String = s"$artist - $title"

  lazy val UFID: String =
    io.morgaroth.media.library.md5HashString(
      s"$url$title$artist$album$startAt$endAt$fadeOutSeconds:$volumeChange"
    )

  def repr: String =
    s"$title, $artist, ${createdAtLocal.toLocalDate}, $url"

object Track:
  def apply(url: String, title: String, artist: String, status: TrackStatus): Track =
    apply(url, title, artist, "", status)

  def apply(url: String, title: String, artist: String, album: String, status: TrackStatus): Track =
    new Track(url, title, artist, album, None, None, None, None, status, TrackId(artist, title), Set.empty, "", "")

  def apply(
    url: String, title: String, artist: String, album: String, status: TrackStatus,
    startAt: Option[String], endAt: Option[String], fadeOutSeconds: Option[Int],
    volumeChange: Option[BigDecimal], playlists: Set[String],
  ): Track =
    new Track(url, title, artist, album, startAt, endAt, fadeOutSeconds, volumeChange, status, TrackId(artist, title), playlists, "", "")

  def apply(url: String): Track =
    new Track(url, "", "", "", None, None, None, None, TrackStatus.Draft, None, Set.empty, "", "")

object TrackId:
  val sep = "___"

  def apply(artist: String, title: String): Option[String] =
    if title.nonEmpty || artist.nonEmpty then Some(s"$artist$sep$title") else None

trait TracksStorage[F[_]]:
  def getById(id: UUID): F[ErrorOr[Track]]
  def save(document: Track): F[ErrorOr[Track]]
  def store(url: String): F[ErrorOr[Track]] = save(Track(url))
  def updateArtist(id: UUID, artist: String): F[ErrorOr[Track]]
  def updateTitle(id: UUID, title: String): F[ErrorOr[Track]]
  def updateAlbum(id: UUID, album: String): F[ErrorOr[Track]]
  def updateStartAt(id: UUID, data: Option[String]): F[ErrorOr[Track]]
  def updateEndAt(id: UUID, data: Option[String]): F[ErrorOr[Track]]
  def updateUrl(id: UUID, url: String): F[ErrorOr[Track]]
  def updateStatus(id: UUID, status: TrackStatus): F[ErrorOr[Track]]
  def updateFadeOutSeconds(id: UUID, newData: Option[Int]): F[ErrorOr[Track]]
  def updateVolumeChange(id: UUID, newData: Option[BigDecimal]): F[ErrorOr[Track]]
  def updatePlaylists(id: UUID, newData: Set[String]): F[ErrorOr[Track]]
  def updateRawTitle(id: UUID, newData: String): F[ErrorOr[Track]]
  def updateRawDescription(id: UUID, newData: String): F[ErrorOr[Track]]
  def findAllPlaylists(): F[ErrorOr[Map[String, Vector[Track]]]]
  def search(
    artist: Option[String] = None,
    title: Option[String] = None,
    statuses: Option[Set[TrackStatus]] = None,
    limit: java.lang.Integer = null,
  ): F[ErrorOr[Vector[Track]]]
  def findAllReadyToFetch: F[Vector[Track]]
  def genericSearch(text: String, page: Int): F[ErrorOr[Vector[Track]]]
