package io.morgaroth.media.library.gui

import java.util.UUID
import cats.syntax.option._
import com.typesafe.scalalogging.LazyLogging
import io.morgaroth.media.library.ErrorOr
import io.morgaroth.media.library.storage.{Draft, Track, TrackStatus, TracksDB}

import scala.util.Random

trait GuiBackend {
  def search(text: String, page: Int): ErrorOr[Vector[Track]]

  def getById(id: UUID): ErrorOr[Track]

  def updateUrl(id: UUID, url: String): ErrorOr[Track]

  def updateEndAt(id: UUID, endAt: Option[String]): ErrorOr[Track]

  def updateStartAt(id: UUID, startAt: Option[String]): ErrorOr[Track]

  def storeUrl(url: String): ErrorOr[Track]

  def updateArtist(id: UUID, artist: String): ErrorOr[Track]

  def updateTitle(id: UUID, title: String): ErrorOr[Track]

  def updateStatus(id: UUID, status: TrackStatus): ErrorOr[Track]

  def nextDraft: ErrorOr[Option[Track]]

  def updateFadeOutSeconds(id: UUID, newData: Option[Int]): ErrorOr[Track]

  def updateVolumeChange(id: UUID, newData: Option[BigDecimal]): ErrorOr[Track]
}

class MongoBackedGuiBackend(storage: TracksDB) extends GuiBackend with LazyLogging {
  override def updateStatus(id: UUID, newStatus: TrackStatus) = storage.updateStatus(id, newStatus)

  override def storeUrl(url: String) = {
    val result = storage.store(url)
    logger.info(s"storing url $url ended with $result")
    result
  }

  override def updateArtist(id: UUID, artist: String) = storage.updateArtist(id, artist)

  override def updateTitle(id: UUID, title: String) = storage.updateTitle(id, title)

  override def nextDraft: ErrorOr[Option[Track]] = storage.search(statuses = Some(Set(Draft))).map {
    allDrafts => if (allDrafts.isEmpty) none else allDrafts(Random.nextInt(allDrafts.size)).some
  }

  override def updateStartAt(id: UUID, startAt: Option[String]) = storage.updateStartAt(id, startAt)

  override def updateEndAt(id: UUID, endAt: Option[String]) = storage.updateEndAt(id, endAt)

  override def updateUrl(id: UUID, url: String) = storage.updateUrl(id, url)

  override def updateFadeOutSeconds(id: UUID, newData: Option[Int]) = storage.updateFadeOutSeconds(id, newData)

  override def updateVolumeChange(id: UUID, newData: Option[BigDecimal]) = storage.updateVolumeChange(id, newData)

  override def search(text: String, page: Int) = storage.genericSearch(text, page)

  override def getById(id: UUID): ErrorOr[Track] = storage.getById(id)
}
