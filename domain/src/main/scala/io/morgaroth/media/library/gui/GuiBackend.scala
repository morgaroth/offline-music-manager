package io.morgaroth.media.library.gui

import cats.syntax.option._
import com.typesafe.scalalogging.LazyLogging
import io.morgaroth.media.library.ErrorOr
import io.morgaroth.media.library.storage.ZioTracksStorageService.ZioTracksStorage
import io.morgaroth.media.library.storage._
import zio.Runtime.default.unsafeRun
import zio.{ULayer, ZIO}

import java.util.UUID
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.Random

class FutureGuiBackend(storage: TracksStorage[Future]) extends LazyLogging with GuiBackend {

  def updateStatus(id: UUID, newStatus: TrackStatus): ErrorOr[Track] = storage.updateStatus(id, newStatus).await()

  def storeUrl(url: String): ErrorOr[Track] = {
    val result = storage.store(url)
    logger.info(s"storing url $url ended with $result")
    result.await()
  }

  def updateArtist(id: UUID, artist: String): ErrorOr[Track] = storage.updateArtist(id, artist).await()

  def updateAlbum(id: UUID, album: String): ErrorOr[Track] = storage.updateAlbum(id, album).await()

  def updateTitle(id: UUID, title: String): ErrorOr[Track] = storage.updateTitle(id, title).await()

  def nextDraft: ErrorOr[Option[Track]] = storage.search(statuses = Some(Set(Draft))).await(1.minute).map {
    allDrafts => if (allDrafts.isEmpty) none else allDrafts(Random.nextInt(allDrafts.size)).some
  }

  def updateStartAt(id: UUID, startAt: Option[String]): ErrorOr[Track] = storage.updateStartAt(id, startAt).await()

  def updateEndAt(id: UUID, endAt: Option[String]): ErrorOr[Track] = storage.updateEndAt(id, endAt).await()

  def updateUrl(id: UUID, url: String): ErrorOr[Track] = storage.updateUrl(id, url).await()

  def updateFadeOutSeconds(id: UUID, newData: Option[Int]): ErrorOr[Track] = storage.updateFadeOutSeconds(id, newData).await()

  def updatePlaylists(id: UUID, newData: Set[String]): ErrorOr[Track] = storage.updatePlaylists(id, newData).await()

  def updateVolumeChange(id: UUID, newData: Option[BigDecimal]): ErrorOr[Track] = storage.updateVolumeChange(id, newData).await()

  def search(text: String, page: Int): ErrorOr[Vector[Track]] = storage.genericSearch(text, page).await()

  def getById(id: UUID): ErrorOr[Track] = storage.getById(id).await()

}

class ZioGuiBackend(layer: ULayer[ZioTracksStorage]) extends LazyLogging with GuiBackend {

  private def exec[A](task: ZIO[ZioTracksStorage, Throwable, A]): ErrorOr[A] = {
    unsafeRun(task.provideLayer(layer).run.map(_.toEither))
  }

  def updateStatus(id: UUID, newStatus: TrackStatus): ErrorOr[Track] = exec(ZioTracksStorageService.updateStatus(id, newStatus))

  def storeUrl(url: String): ErrorOr[Track] = exec(ZioTracksStorageService.storeUrl(url))

  def updateArtist(id: UUID, artist: String): ErrorOr[Track] = exec(ZioTracksStorageService.updateArtist(id, artist))

  def updateAlbum(id: UUID, album: String): ErrorOr[Track] = exec(ZioTracksStorageService.updateAlbum(id, album))

  def updateTitle(id: UUID, title: String): ErrorOr[Track] = exec(ZioTracksStorageService.updateTitle(id, title))

  def nextDraft: ErrorOr[Option[Track]] = {
    exec(ZioTracksStorageService.search(statuses = Some(Set(Draft))).map {
      allDrafts => if (allDrafts.isEmpty) none else allDrafts(Random.nextInt(allDrafts.size)).some
    })
  }

  def updateStartAt(id: UUID, startAt: Option[String]): ErrorOr[Track] = exec(ZioTracksStorageService.updateStartAt(id, startAt))

  def updateEndAt(id: UUID, endAt: Option[String]): ErrorOr[Track] = exec(ZioTracksStorageService.updateEndAt(id, endAt))

  def updateUrl(id: UUID, url: String): ErrorOr[Track] = exec(ZioTracksStorageService.updateUrl(id, url))

  def updateFadeOutSeconds(id: UUID, newData: Option[Int]): ErrorOr[Track] = exec(ZioTracksStorageService.updateFadeOutSeconds(id, newData))

  def updatePlaylists(id: UUID, newData: Set[String]): ErrorOr[Track] = exec(ZioTracksStorageService.updatePlaylists(id, newData))

  def updateVolumeChange(id: UUID, newData: Option[BigDecimal]): ErrorOr[Track] = exec(ZioTracksStorageService.updateVolumeChange(id, newData))

  def search(text: String, page: Int): ErrorOr[Vector[Track]] = exec(ZioTracksStorageService.genericSearch(text, page))

  def getById(id: UUID): ErrorOr[Track] = exec(ZioTracksStorageService.getById(id))

}

trait GuiBackend {

  def updateStatus(id: UUID, newStatus: TrackStatus): ErrorOr[Track]

  def storeUrl(url: String): ErrorOr[Track]

  def updateArtist(id: UUID, artist: String): ErrorOr[Track]

  def updateAlbum(id: UUID, album: String): ErrorOr[Track]

  def updateTitle(id: UUID, title: String): ErrorOr[Track]

  def nextDraft: ErrorOr[Option[Track]]

  def updateStartAt(id: UUID, startAt: Option[String]): ErrorOr[Track]

  def updateEndAt(id: UUID, endAt: Option[String]): ErrorOr[Track]

  def updateUrl(id: UUID, url: String): ErrorOr[Track]

  def updateFadeOutSeconds(id: UUID, newData: Option[Int]): ErrorOr[Track]

  def updatePlaylists(id: UUID, newData: Set[String]): ErrorOr[Track]

  def updateVolumeChange(id: UUID, newData: Option[BigDecimal]): ErrorOr[Track]

  def search(text: String, page: Int): ErrorOr[Vector[Track]]

  def getById(id: UUID): ErrorOr[Track]
}
