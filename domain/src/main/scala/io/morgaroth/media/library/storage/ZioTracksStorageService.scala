package io.morgaroth.media.library.storage

import zio.*

import java.util.UUID

trait ZioTracksStorageService:
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
    artist: Option[String] = None,
    title: Option[String] = None,
    statuses: Option[Set[TrackStatus]] = None,
    limit: java.lang.Integer = null,
  ): Task[Vector[Track]]
  def findAllReadyToFetch: Task[Vector[Track]]
  def genericSearch(text: String, page: Int): Task[Vector[Track]]

object ZioTracksStorageService:
  def getById(id: UUID): ZIO[ZioTracksStorageService, Throwable, Track] =
    ZIO.serviceWithZIO[ZioTracksStorageService](_.getById(id))

  def updateUrl(id: UUID, newValue: String): ZIO[ZioTracksStorageService, Throwable, Track] =
    ZIO.serviceWithZIO[ZioTracksStorageService](_.updateUrl(id, newValue))

  def updateArtist(id: UUID, newValue: String): ZIO[ZioTracksStorageService, Throwable, Track] =
    ZIO.serviceWithZIO[ZioTracksStorageService](_.updateArtist(id, newValue))

  def updateAlbum(id: UUID, newValue: String): ZIO[ZioTracksStorageService, Throwable, Track] =
    ZIO.serviceWithZIO[ZioTracksStorageService](_.updateAlbum(id, newValue))

  def updateTitle(id: UUID, newValue: String): ZIO[ZioTracksStorageService, Throwable, Track] =
    ZIO.serviceWithZIO[ZioTracksStorageService](_.updateTitle(id, newValue))

  def updateStatus(id: UUID, newValue: TrackStatus): ZIO[ZioTracksStorageService, Throwable, Track] =
    ZIO.serviceWithZIO[ZioTracksStorageService](_.updateStatus(id, newValue))

  def storeUrl(url: String): ZIO[ZioTracksStorageService, Throwable, Track] =
    ZIO.serviceWithZIO[ZioTracksStorageService](_.store(url))

  def updateStartAt(id: UUID, data: Option[String]): ZIO[ZioTracksStorageService, Throwable, Track] =
    ZIO.serviceWithZIO[ZioTracksStorageService](_.updateStartAt(id, data))

  def updateEndAt(id: UUID, data: Option[String]): ZIO[ZioTracksStorageService, Throwable, Track] =
    ZIO.serviceWithZIO[ZioTracksStorageService](_.updateEndAt(id, data))

  def updateFadeOutSeconds(id: UUID, data: Option[Int]): ZIO[ZioTracksStorageService, Throwable, Track] =
    ZIO.serviceWithZIO[ZioTracksStorageService](_.updateFadeOutSeconds(id, data))

  def updatePlaylists(id: UUID, newData: Set[String]): ZIO[ZioTracksStorageService, Throwable, Track] =
    ZIO.serviceWithZIO[ZioTracksStorageService](_.updatePlaylists(id, newData))

  def updateVolumeChange(id: UUID, newData: Option[BigDecimal]): ZIO[ZioTracksStorageService, Throwable, Track] =
    ZIO.serviceWithZIO[ZioTracksStorageService](_.updateVolumeChange(id, newData))

  def genericSearch(text: String, page: Int): ZIO[ZioTracksStorageService, Throwable, Vector[Track]] =
    ZIO.serviceWithZIO[ZioTracksStorageService](_.genericSearch(text, page))

  def search(
    artist: Option[String] = None,
    title: Option[String] = None,
    statuses: Option[Set[TrackStatus]] = None,
    limit: java.lang.Integer = null,
  ): ZIO[ZioTracksStorageService, Throwable, Vector[Track]] =
    ZIO.serviceWithZIO[ZioTracksStorageService](_.search(artist, title, statuses, limit))
