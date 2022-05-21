package io.morgaroth.media.library.gui

import cats.syntax.option._
import com.typesafe.scalalogging.LazyLogging
import io.morgaroth.media.library.ErrorOr
import io.morgaroth.media.library.storage._
import zio.Runtime.default.unsafeRun
import zio.Task

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

class ZioGuiBackend(layer: ZioTracksStorageService) extends LazyLogging with GuiBackend {

  private def exec[A](task: Task[A]): ErrorOr[A] = {
    unsafeRun(task.run.map(_.toEither))
  }

  def updateStatus(id: UUID, newStatus: TrackStatus): ErrorOr[Track] = exec(layer.updateStatus(id, newStatus))

  def storeUrl(url: String): ErrorOr[Track] = exec(layer.store(url))

  def updateArtist(id: UUID, artist: String): ErrorOr[Track] = exec(layer.updateArtist(id, artist))

  def updateAlbum(id: UUID, album: String): ErrorOr[Track] = exec(layer.updateAlbum(id, album))

  def updateTitle(id: UUID, title: String): ErrorOr[Track] = exec(layer.updateTitle(id, title))

  def nextDraft: ErrorOr[Option[Track]] = {
    exec(layer.search(statuses = Some(Set(Draft))).map {
      allDrafts => if (allDrafts.isEmpty) none else allDrafts(Random.nextInt(allDrafts.size)).some
    })
  }

  def updateStartAt(id: UUID, startAt: Option[String]): ErrorOr[Track] = exec(layer.updateStartAt(id, startAt))

  def updateEndAt(id: UUID, endAt: Option[String]): ErrorOr[Track] = exec(layer.updateEndAt(id, endAt))

  def updateUrl(id: UUID, url: String): ErrorOr[Track] = exec(layer.updateUrl(id, url))

  def updateFadeOutSeconds(id: UUID, newData: Option[Int]): ErrorOr[Track] = exec(layer.updateFadeOutSeconds(id, newData))

  def updatePlaylists(id: UUID, newData: Set[String]): ErrorOr[Track] = exec(layer.updatePlaylists(id, newData))

  def updateVolumeChange(id: UUID, newData: Option[BigDecimal]): ErrorOr[Track] = exec(layer.updateVolumeChange(id, newData))

  def search(text: String, page: Int): ErrorOr[Vector[Track]] = exec(layer.genericSearch(text, page))

  def getById(id: UUID): ErrorOr[Track] = exec(layer.getById(id))

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
