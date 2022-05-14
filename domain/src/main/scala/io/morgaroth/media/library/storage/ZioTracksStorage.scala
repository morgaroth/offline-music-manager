package io.morgaroth.media.library.storage

import zio._

import java.util.UUID
import scala.language.higherKinds

object ZioTracksStorage {
  type TService = Has[Service]

  trait Service {
    def getById(id: UUID): IO[Throwable, Track]

    def updateProperty(id: UUID, name: String, newValue: String): Task[Unit]

    def save(document: Track): IO[Throwable, Track]

    def store(url: String): IO[Throwable, Track] = save(Track(url))

    def updateArtist(id: UUID, artist: String): IO[Throwable, Track]

    def updateTitle(id: UUID, title: String): IO[Throwable, Track]

    def updateAlbum(id: UUID, album: String): IO[Throwable, Track]

    def updateStartAt(id: UUID, data: Option[String]): IO[Throwable, Track]

    def updateEndAt(id: UUID, data: Option[String]): IO[Throwable, Track]

    def updateUrl(id: UUID, url: String): IO[Throwable, Track]

    def updateStatus(id: UUID, status: TrackStatus): IO[Throwable, Track]

    def updateFadeOutSeconds(id: UUID, newData: Option[Int]): IO[Throwable, Track]

    def updateVolumeChange(id: UUID, newData: Option[BigDecimal]): IO[Throwable, Track]

    def updatePlaylists(id: UUID, newData: Set[String]): IO[Throwable, Track]

    def updateRawTitle(id: UUID, newData: String): IO[Throwable, Track]

    def updateRawDescription(id: UUID, newData: String): IO[Throwable, Track]

    def findAllPlaylists(): IO[Throwable, Map[String, Vector[Track]]]

    def search(
                artist: Option[String] = None, title: Option[String] = None,
                statuses: Option[Set[TrackStatus]] = None,
                limit: java.lang.Integer = null
              ): IO[Throwable, Vector[Track]]

    def findAllReadyToFetch: IO[Throwable, Vector[Track]]

    def genericSearch(text: String, page: Int): IO[Throwable, Vector[Track]]
  }

  def getById(id: UUID): RIO[TService, Track] = ZIO.accessM[TService](_.get.getById(id))

  def update(id: UUID)(f: RIO[TService, _]): RIO[TService, Track] = {
    for {
      _ <- getById(id)
      _ <- f
      updated <- getById(id)
    } yield updated
  }

  def updateTitle(id: UUID, newValue: String): RIO[TService, Track] = {
    update(id)(ZIO.accessM[TService](_.get.updateTitle(id, newValue)))
  }

  def updateArtist(id: UUID, newValue: String): RIO[TService, Track] = {
    update(id)(ZIO.accessM[TService](_.get.updateArtist(id, newValue)))
  }

  def updateEndAt(id: UUID, newValue: Option[String]): RIO[TService, Track] = {
    update(id)(ZIO.accessM[TService](_.get.updateEndAt(id, newValue)))
  }

  def updateFadeOutSeconds(id: UUID, newValue: Option[Int]): RIO[TService, Track] = {
    update(id)(ZIO.accessM[TService](_.get.updateFadeOutSeconds(id, newValue)))
  }

  val findAllReadyToFetch: ZIO[TService, Throwable, Vector[Track]] = ZIO.accessM[TService](_.get.findAllReadyToFetch)

  def updateUrl(id: UUID, newValue: String): RIO[TService, Track] = {
    update(id)(ZIO.accessM[TService](_.get.updateUrl(id, newValue)))
  }


}
