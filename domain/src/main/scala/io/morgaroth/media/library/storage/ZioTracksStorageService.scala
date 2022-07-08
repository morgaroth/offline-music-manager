package io.morgaroth.media.library.storage

import zio._

import java.util.UUID

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
  def getById(id: UUID): Task[Track]

  def save(document: Track): Task[Unit]

  def store(url: String): Task[Track]

  def updateArtist(id: UUID, artist: String): Task[Track]

  def updateTitle(id: UUID, title: String): Task[Track]

  def updateAlbum(id: UUID, album: String): Task[Track]

  def updateStartAt(id: UUID, data: Option[String]): Task[Track]

  def updateEndAt(id: UUID, data: Option[String]): Task[Track]

  def updateUrl(id: UUID, url: String): Task[Track]

  def updateStatus(id: UUID, status: TrackStatus): Task[Track]

  def updateFadeOutSeconds(id: UUID, newData: Option[Int]): Task[Track]

  def updateVolumeChange(id: UUID, newData: Option[BigDecimal]): Task[Track]

  def updatePlaylists(id: UUID, newData: Set[String]): Task[Track]

  def updateRawTitle(id: UUID, newData: String): Task[Track]

  def updateRawDescription(id: UUID, newData: String): Task[Track]

  def findAllPlaylists(): Task[Map[String, Vector[Track]]]

  def search(
              artist: Option[String] = None, title: Option[String] = None,
              statuses: Option[Set[TrackStatus]] = None,
              limit: java.lang.Integer = null
            ): Task[Vector[Track]]

  def findAllReadyToFetch: Task[Vector[Track]]

  def genericSearch(text: String, page: Int): Task[Vector[Track]]
}

