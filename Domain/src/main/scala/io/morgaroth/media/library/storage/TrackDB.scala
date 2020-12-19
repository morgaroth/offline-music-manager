package io.morgaroth.media.library.storage

import cats.implicits._
import io.morgaroth.media.library.ErrorOr
import org.joda.time.LocalDateTime

import java.net.URLEncoder
import java.util.UUID

case class TrackNotFound(desc: String) extends Exception(s"track not found $desc")

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
                  playlists: Set[String] = Set.empty,
                  rawTitle: Option[String] = Some(""),
                  rawDescription: Option[String] = Some(""),
                  updatedAt: LocalDateTime = LocalDateTime.now(),
                  createdAt: LocalDateTime = LocalDateTime.now(),
                  _id: UUID = UUID.randomUUID(),
                ) {
  lazy val searchUrl = s"https://www.youtube.com/results?search_query=${URLEncoder.encode(s"$title $artist", "utf-8")}"

  lazy val isReadyToFetch: Boolean = title.nonEmpty && artist.nonEmpty && status == Final

  lazy val info = s"$artist - $title"
  lazy val UFID: String = io.morgaroth.media.library.md5HashString(s"$url$title$artist$album$startAt$endAt$fadeOutSeconds:$volumeChange")

  def repr: String = {
    {
      new StringBuilder() ++= title ++= ", " ++= artist ++=", " ++= createdAt.toLocalDate.toString() ++= ", " ++= url
    }.mkString
  }
}

object Track {
  def apply(
             url: String,
             title: String,
             artist: String,
             status: TrackStatus,
           ): Track = {
    apply(url, title, artist, "", status)
  }

  def apply(
             url: String,
             title: String,
             artist: String,
             album: String,
             status: TrackStatus,
           ): Track = {
    new Track(url, title, artist, album, none, none, none, none, status, TrackId(artist, title), Set.empty)
  }

  def apply(
             url: String,
             title: String,
             artist: String,
             album: String,
             status: TrackStatus,
             startAt: Option[String],
             endAt: Option[String],
             fadeOutSeconds: Option[Int],
             volumeChange: Option[BigDecimal],
             playlists: Set[String],
           ): Track = {
    new Track(url, title, artist, album, startAt, endAt, fadeOutSeconds, volumeChange, status, TrackId(artist, title), playlists)
  }

  def apply(url: String): Track = {
    new Track(url, "", "", "", None, None, None, None, Draft, None, Set.empty)
  }
}

object TrackId {
  val sep = "___"

  def apply(artist: String, title: String): Option[String] =
    if (title.nonEmpty || artist.nonEmpty) s"$artist$sep$title".some else none
}

trait TracksStorage[F[_]] {

  def getById(id: UUID): F[ErrorOr[Track]]
  def getBy(artist: String, title: String): F[ErrorOr[Track]]
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
  def updateRawTitle(id: UUID, newData: Option[String]): F[ErrorOr[Track]]
  def updateRawDescription(id: UUID, newData: Option[String]): F[ErrorOr[Track]]
  def findAllPlaylists(): F[ErrorOr[Map[String, Vector[Track]]]]

  def search(
              artist: Option[String] = None, title: Option[String] = None,
              statuses: Option[Set[TrackStatus]] = None,
              limit: java.lang.Integer = null
            ): F[ErrorOr[Vector[Track]]]

  def findAllReadyToFetch: F[Vector[Track]]
  def genericSearch(text: String, page: Int): F[ErrorOr[Vector[Track]]]
}

