package io.morgaroth.media.library.gui

import cats.syntax.option._
import com.typesafe.scalalogging.LazyLogging
import io.morgaroth.media.library.ErrorOr
import io.morgaroth.media.library.storage.{Draft, Track, TrackStatus, TracksStorage}

import java.util.UUID
import scala.concurrent.{Await, Future}
import scala.util.Random

class GuiBackend(storage: TracksStorage[Future]) extends LazyLogging {

  def updateStatus(id: UUID, newStatus: TrackStatus): ErrorOr[Track] = storage.updateStatus(id, newStatus).await()

  def storeUrl(url: String): ErrorOr[Track] = {
    val result = storage.store(url)
    logger.info(s"storing url $url ended with $result")
    result.await()
  }

  def updateArtist(id: UUID, artist: String): ErrorOr[Track] = storage.updateArtist(id, artist).await()

  def updateAlbum(id: UUID, album: String): ErrorOr[Track] = storage.updateAlbum(id, album).await()

  def updateTitle(id: UUID, title: String): ErrorOr[Track] = storage.updateTitle(id, title).await()

  def nextDraft: ErrorOr[Option[Track]] = storage.search(statuses = Some(Set(Draft))).await().map {
    allDrafts => if (allDrafts.isEmpty) none else allDrafts(Random.nextInt(allDrafts.size)).some
  }

  def updateStartAt(id: UUID, startAt: Option[String]): ErrorOr[Track] = storage.updateStartAt(id, startAt).await()

  def updateEndAt(id: UUID, endAt: Option[String]): ErrorOr[Track] = storage.updateEndAt(id, endAt).await()

  def updateUrl(id: UUID, url: String): ErrorOr[Track] = storage.updateUrl(id, url).await()

  def updateFadeOutSeconds(id: UUID, newData: Option[Int]): ErrorOr[Track] = storage.updateFadeOutSeconds(id, newData).await()

  def updateVolumeChange(id: UUID, newData: Option[BigDecimal]): ErrorOr[Track] = storage.updateVolumeChange(id, newData).await()

  def search(text: String, page: Int): ErrorOr[Vector[Track]] = storage.genericSearch(text, page).await()

  def getById(id: UUID): ErrorOr[Track] = storage.getById(id).await()
}
