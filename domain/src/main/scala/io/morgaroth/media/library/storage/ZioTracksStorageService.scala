package io.morgaroth.media.library.storage

import zio._

import java.util.UUID
import scala.language.higherKinds

object ZioTracksStorageService {
  type TService = Has[ZioTracksStorageService]
  type ZioTracksStorage = Has[ZioTracksStorageService]

  def getById(id: UUID): ZIO[TService, Throwable, Track] = {
    ZIO.accessM[TService](_.get.getById(id))
  }

  def updateUrl(id: UUID, newValue: String): ZIO[TService, Throwable, Track] = {
    ZIO.accessM[TService](_.get.updateUrl(id, newValue))
  }

  def updateArtist(id: UUID, newValue: String): ZIO[TService, Throwable, Track] = {
    ZIO.accessM[TService](_.get.updateArtist(id, newValue))
  }

  def updateAlbum(id: UUID, newValue: String): ZIO[TService, Throwable, Track] = {
    ZIO.accessM[TService](_.get.updateAlbum(id, newValue))
  }

  def updateTitle(id: UUID, newValue: String): ZIO[TService, Throwable, Track] = {
    ZIO.accessM[TService](_.get.updateTitle(id, newValue))
  }

  def updateStatus(id: UUID, newValue: TrackStatus): ZIO[TService, Throwable, Track] = {
    ZIO.accessM[TService](_.get.updateStatus(id, newValue))
  }

  def storeUrl(url: String): ZIO[TService, Throwable, Track] = {
    ZIO.accessM[TService](_.get.store(url))
  }

  def updateStartAt(id: UUID, data: Option[String]): ZIO[TService, Throwable, Track] = {
    ZIO.accessM[TService](_.get.updateStartAt(id, data))
  }

  def updateEndAt(id: UUID, data: Option[String]): ZIO[TService, Throwable, Track] = {
    ZIO.accessM[TService](_.get.updateEndAt(id, data))
  }

  def updateFadeOutSeconds(id: UUID, data: Option[Int]): ZIO[TService, Throwable, Track] = {
    ZIO.accessM[TService](_.get.updateFadeOutSeconds(id, data))
  }

  def updatePlaylists(id: UUID, newData: Set[String]): ZIO[TService, Throwable, Track] = {
    ZIO.accessM[TService](_.get.updatePlaylists(id, newData))
  }

  def updateVolumeChange(id: UUID, newData: Option[BigDecimal]): ZIO[TService, Throwable, Track] = {
    ZIO.accessM[TService](_.get.updateVolumeChange(id, newData))
  }

  def genericSearch(text: String, page: Int): ZIO[TService, Throwable, Vector[Track]] = {
    ZIO.accessM[TService](_.get.genericSearch(text, page))
  }

  def search(artist: Option[String] = None, title: Option[String] = None,
             statuses: Option[Set[TrackStatus]] = None,
             limit: java.lang.Integer = null): ZIO[TService, Throwable, Vector[Track]] = {
    ZIO.accessM[TService](_.get.search(artist, title, statuses, limit))
  }
}

trait ZioTracksStorageService {
  def getById(id: UUID): IO[Throwable, Track]

  def save(document: Track): Task[Unit]

  def store(url: String): IO[Throwable, Track]

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

