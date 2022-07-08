package io.morgaroth.media.library.gui

import cats.syntax.option._
import com.typesafe.scalalogging.LazyLogging
import io.morgaroth.media.library.ErrorOr
import io.morgaroth.media.library.storage.ZioTracksStorageService.ZioTracksStorage
import io.morgaroth.media.library.storage._
import zio.Runtime.default.unsafeRun
import zio.{Has, Task, ZLayer}

import java.util.UUID
import scala.util.Random

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

object ZioGuiBackend {
  val live: ZLayer[ZioTracksStorage, Nothing, Has[ZioGuiBackend]] = ZLayer.fromService(new ZioGuiBackend(_))

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
