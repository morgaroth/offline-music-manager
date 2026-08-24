package io.morgaroth.media.library.gui

import io.morgaroth.media.library.storage.*
import zio.*

import java.util.UUID
import scala.util.Random

/** High-level GUI backend service built on ZioTracksStorageService.
  * All methods return Task (ZIO effects) — the UI layer uses FxBridge
  * to dispatch them and collect results on the JavaFX thread.
  */
trait GuiBackend:
  def getById(id: UUID): Task[Track]
  def storeUrl(url: String): Task[Track]
  def updateArtist(id: UUID, artist: String): Task[Track]
  def updateAlbum(id: UUID, album: String): Task[Track]
  def updateTitle(id: UUID, title: String): Task[Track]
  def updateUrl(id: UUID, url: String): Task[Track]
  def updateStatus(id: UUID, newStatus: TrackStatus): Task[Track]
  def updateStartAt(id: UUID, startAt: Option[String]): Task[Track]
  def updateEndAt(id: UUID, endAt: Option[String]): Task[Track]
  def updateFadeOutSeconds(id: UUID, newData: Option[Int]): Task[Track]
  def updateVolumeChange(id: UUID, newData: Option[BigDecimal]): Task[Track]
  def updatePlaylists(id: UUID, newData: Set[String]): Task[Track]
  def nextDraft: Task[Option[Track]]
  def search(text: String, page: Int): Task[Vector[Track]]

object GuiBackend:
  val live: ZLayer[ZioTracksStorageService, Nothing, GuiBackend] =
    ZLayer.fromFunction(GuiBackendLive(_))

class GuiBackendLive(storage: ZioTracksStorageService) extends GuiBackend:
  def getById(id: UUID): Task[Track] = storage.getById(id)

  def storeUrl(url: String): Task[Track] = storage.store(url)

  def updateArtist(id: UUID, artist: String): Task[Track] = storage.updateArtist(id, artist)

  def updateAlbum(id: UUID, album: String): Task[Track] = storage.updateAlbum(id, album)

  def updateTitle(id: UUID, title: String): Task[Track] = storage.updateTitle(id, title)

  def updateUrl(id: UUID, url: String): Task[Track] = storage.updateUrl(id, url)

  def updateStatus(id: UUID, newStatus: TrackStatus): Task[Track] = storage.updateStatus(id, newStatus)

  def updateStartAt(id: UUID, startAt: Option[String]): Task[Track] = storage.updateStartAt(id, startAt)

  def updateEndAt(id: UUID, endAt: Option[String]): Task[Track] = storage.updateEndAt(id, endAt)

  def updateFadeOutSeconds(id: UUID, newData: Option[Int]): Task[Track] = storage.updateFadeOutSeconds(id, newData)

  def updateVolumeChange(id: UUID, newData: Option[BigDecimal]): Task[Track] = storage.updateVolumeChange(id, newData)

  def updatePlaylists(id: UUID, newData: Set[String]): Task[Track] = storage.updatePlaylists(id, newData)

  def nextDraft: Task[Option[Track]] =
    storage.search(statuses = Some(Set(TrackStatus.Draft))).map: allDrafts =>
      if allDrafts.isEmpty then None
      else Some(allDrafts(Random.nextInt(allDrafts.size)))

  def search(text: String, page: Int): Task[Vector[Track]] = storage.genericSearch(text, page)
