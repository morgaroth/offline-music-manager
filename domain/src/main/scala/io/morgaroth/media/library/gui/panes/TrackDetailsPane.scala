package io.morgaroth.media.library.gui.panes

import cats.syntax.option._
import com.typesafe.scalalogging.LazyLogging
import io.gitlab.mateuszjaje.gnome.scala._
import io.morgaroth.media.library.ErrorOr
import io.morgaroth.media.library.gui.GuiBackend
import io.morgaroth.media.library.jobs.{MetaDataFetcher, YoutubeDLMeta}
import io.morgaroth.media.library.storage.{Deleted, Draft, Final, Track}
import org.gnome.gtk

import scala.sys.process._

class TrackDetailsPane(
                        width: Int,
                        height: Int,
                        backend: GuiBackend,
                      ) extends gtk.VBox(false, 1) with LazyLogging {

  val labelBtnWidth = 200
  val saveBtnWidth = 100
  val inputWidth = width - labelBtnWidth - saveBtnWidth

  var trackUnderWork: Option[Track] = None

  private val singleDigit = """\s*(\d)\s*""".r
  private val twoDigits = """\s*(\d\d)\s*""".r

  private def normalizeTimeValue(rawValue: String) = {
    rawValue.trim.replaceAll("[;:,]", ":") match {
      case singleDigit(seconds) => s"0:0$seconds"
      case twoDigits(seconds) => s"0:$seconds"
      case another => another
    }
  }

  private val nameStripped = """^[\s-/]*(.+?)[\s-/]*$""".r

  private def normalizeName(rawValue: String) = {
    rawValue
      .replaceAllLiterally("Official Video", "")
      .replaceAllLiterally("official video", "")
      .replaceAllLiterally("official audio", "")
      .replaceAllLiterally("Lyrics", "")
      .replaceAllLiterally("()", "")
      .trim match {
      case nameStripped(name) => name
      case "" => ""
    }
  }

  private val lineHeight = 40
  private val urlEdit = Edit().disabled.withSizeRequest(inputWidth, lineHeight)
  private val titleEdit = Edit().disabled.withSizeRequest(inputWidth, lineHeight)
  private val artistEdit = Edit().disabled.withSizeRequest(inputWidth, lineHeight)
  private val albumEdit = Edit().disabled.withSizeRequest(inputWidth, lineHeight)

  private val updateUrlBtn = Btn("zapisz").onClick(_ => trackUnderWork.foreach { track =>
    urlEdit.getText.trim.some.filter(_ != track.url).map(backend.updateUrl(track._id, _)).map(load)
  }).disabled.withSizeRequest(saveBtnWidth, lineHeight)

  private val openBtn = Btn("Otwórz!").onClick(_ => trackUnderWork.foreach {
    track => s"google-chrome ${track.url}".!
  }).disabled.withSizeRequest(saveBtnWidth, lineHeight)

  private val searchYTBtn = Btn("Szukaj w YT!").onClick(_ => trackUnderWork.foreach { track =>
    s"google-chrome ${track.searchUrl}".!
  }).disabled.withSizeRequest(saveBtnWidth, lineHeight)

  private val artistSave = Btn("zapisz").onClick(_ => trackUnderWork.foreach { track =>
    normalizeName(artistEdit.getText).some.filter(_ != track.artist).map { artist =>
      logger.info(s"updating ${track._id}/artist to $artist")
      backend.updateArtist(track._id, artist)
    }.map(load)
  }).disabled.withSizeRequest(saveBtnWidth, lineHeight)

  private val albumSave = Btn("zapisz").onClick(_ => trackUnderWork.foreach { track =>
    normalizeName(albumEdit.getText).some.filter(_ != track.album).map { album =>
      logger.info(s"updating ${track._id}/album to $album")
      backend.updateAlbum(track._id, album)
    }.map(load)
  }).disabled.withSizeRequest(saveBtnWidth, lineHeight)

  private val titleSave = Btn("zapisz").onClick(_ => trackUnderWork.foreach { track =>
    normalizeName(titleEdit.getText).some.filter(_ != track.title).map { title =>
      logger.info(s"updating ${track._id}/title to $title")
      backend.updateTitle(track._id, title)
    }.map(load)
  }).disabled.withSizeRequest(saveBtnWidth, lineHeight)

  //  private val startAtCheckBtn = Checkbox("").disabled
  //  private val endAtCheckBtn = Checkbox("").disabled
  //  private val fadeCheckBtn = Checkbox("").disabled
  //  private val volumeCheckBtn = Checkbox("").disabled
  private val startAtEdit = Edit().disabled.withSizeRequest(inputWidth, lineHeight)
  private val endAtEdit = Edit().disabled.withSizeRequest(inputWidth, lineHeight)
  private val fadeEdit = Edit().disabled.withSizeRequest(inputWidth, lineHeight)
  //  private val volumeEdit = Edit().disabled
  private val playlistsEdit = Edit().disabled.withSizeRequest(inputWidth + saveBtnWidth, lineHeight)
  private val startAtSave = Btn("Zapisz").disabled.withSizeRequest(saveBtnWidth, lineHeight)
  private val endAtSave = Btn("Zapisz").disabled.withSizeRequest(saveBtnWidth, lineHeight)
  private val fadeSave = Btn("Zapisz").disabled.withSizeRequest(saveBtnWidth, lineHeight)
  private val playlistSave = Btn("Zapisz").disabled.withSizeRequest(saveBtnWidth, lineHeight)
  private val volumeSave = Btn("Zapisz").disabled.withSizeRequest(saveBtnWidth, lineHeight)
  private val deleteBtn = Btn("Usuń").disabled
  private val doneBtn = Btn("Zapisz jako gotowe").disabled
  private val draftBtn = Btn("Zapisz jako szkic").disabled

  //  startAtCheckBtn.onToggle((x, _) => {
  //    startAtEdit.enable(x)
  //    startAtSave.enabled
  //    if (!x) startAtEdit.setText("")
  //  })
  //
  //  endAtCheckBtn.onToggle((x, _) => {
  //    endAtEdit.enable(x)
  //    endAtSave.enabled
  //    if (!x) endAtEdit.setText("")
  //  })
  //
  //  fadeCheckBtn.onToggle((x, _) => {
  //    fadeEdit.enable(x)
  //    fadeSave.enabled
  //    if (!x) fadeEdit.setText("")
  //  })

  //  volumeCheckBtn.onToggle((x, _) => {
  //    volumeEdit.enable(x)
  //    volumeSave.enabled
  //    if (!x) volumeEdit.setText("")
  //  })

  startAtSave.onClick { _ =>
    trackUnderWork.foreach { track =>
      val rawInput = Option(startAtEdit.getText)
      rawInput.map(normalizeTimeValue).foreach {
        case validValue if validValue.matches("""^\d?\d:\d\d$""") =>
          logger.info(s"updating ${track._id}/startAt to $validValue")
          load(backend.updateStartAt(track._id, Some(validValue)))
        case "" =>
          logger.info(s"removing ${track._id}/startAt")
          load(backend.updateStartAt(track._id, None))
        case invalidOne =>
          logger.warn(s"invalid data value='$invalidOne' (raw input: ${rawInput})")
      }
    }
  }

  endAtSave.onClick { _ =>
    trackUnderWork.foreach { track =>
      val rawInput = Option(endAtEdit.getText)
      rawInput.map(normalizeTimeValue).foreach {
        case validValue if validValue.matches("""^\d?\d:\d\d$""") =>
          logger.info(s"updating ${track._id}/endAt to $validValue")
          load(backend.updateEndAt(track._id, Some(validValue)))
        case "" =>
          logger.info(s"removing ${track._id}/endAt")
          load(backend.updateEndAt(track._id, None))
        case invalidOne =>
          logger.warn(s"invalid data value='$invalidOne' (raw input: ${rawInput})")
      }
    }
  }

  fadeSave.onClick { _ =>
    trackUnderWork.foreach { track =>
      val rawInput = Option(fadeEdit.getText)
      rawInput.foreach {
        case validValue if validValue.matches("""^\d+$""") =>
          logger.info(s"updating ${track._id}/fadeOutSeconds to $validValue")
          load(backend.updateFadeOutSeconds(track._id, Some(validValue.toInt)))
        case "" =>
          logger.info(s"removing ${track._id}/fadeOutSeconds")
          load(backend.updateFadeOutSeconds(track._id, None))
        case invalidOne =>
          logger.warn(s"invalid data value='$invalidOne'm raw='$rawInput'")
      }
    }
  }

  playlistSave.onClick { _ =>
    trackUnderWork.foreach { track =>
      val rawInput = Option(playlistsEdit.getText).map(_.trim)
      rawInput.foreach {
        case "" =>
          logger.info(s"clearing ${track._id}/playlists")
          load(backend.updatePlaylists(track._id, Set.empty))
        case validValue if validValue.matches("""^([a-z]+\h*,\h*)*[a-z]+\h*$""") =>
          val parsed = validValue.split(",").map(_.trim).filter(_.nonEmpty).sorted
          logger.info(s"updating ${track._id}/playlists to '${parsed.mkString(", ")}'")
          load(backend.updatePlaylists(track._id, parsed.toSet))
        case invalidOne =>
          logger.warn(s"invalid data value='$invalidOne'm raw='$rawInput'")
      }
    }
  }

  //  volumeSave.onClick { _ =>
  //    trackUnderWork.foreach { track =>
  //      val pData = volumeCheckBtn.getActive
  //      val v = Option(volumeEdit.getText).filter(_.matches("""^\d+(\.\d+)?$""")).map(BigDecimal(_))
  //      if (pData && v.isDefined) {
  //        val action = s"updating ${track.id}/volumeChange to $v"
  //        logger.info(action)
  //        load(backend.updateVolumeChange(track.id, v)).left.map(err => logger.warn(s"$action finished with error $err"))
  //      } else if (!pData) {
  //        val action = s"removing ${track.id}/volumeChange"
  //        logger.info(action)
  //        load(backend.updateVolumeChange(track.id, None)).left.map(err => logger.warn(s"$action finished with error $err"))
  //      } else {
  //        logger.warn(s"invalid data checked=$pData, value='$v'")
  //        Right(track)
  //      }
  //    }
  //  }

  deleteBtn.onClick(_ => trackUnderWork.map { track =>
    logger.info(s"updating ${track._id}/status to ${Deleted.dbRepr}")
    backend.updateStatus(track._id, Deleted)
  }.map(_ => loadDraft()))

  doneBtn.onClick(_ => trackUnderWork.map { track =>
    logger.info(s"updating ${track._id}/status to ${Final.dbRepr}")
    backend.updateStatus(track._id, Final)
  }.map(load))

  draftBtn.onClick(_ => trackUnderWork.map { track =>
    logger.info(s"updating ${track._id}/status to ${Draft.dbRepr}")
    backend.updateStatus(track._id, Draft)
  }.map(load))

  private def loadControls() = {
    urlEdit.setText(trackUnderWork.orEmpty(_.url))
    urlEdit.enable(trackUnderWork.isDefined)
    updateUrlBtn.enable(trackUnderWork.isDefined)
    openBtn.enable(trackUnderWork.isDefined)
    searchYTBtn.enable(trackUnderWork.isDefined)

    titleEdit.setText(trackUnderWork.orEmpty(_.title))
    titleEdit.enable(trackUnderWork.isDefined)
    titleSave.enable(trackUnderWork.isDefined)

    artistEdit.setText(trackUnderWork.orEmpty(_.artist))
    artistEdit.enable(trackUnderWork.isDefined)
    artistSave.enable(trackUnderWork.isDefined)

    albumEdit.setText(trackUnderWork.orEmpty(_.album))
    albumEdit.enable(trackUnderWork.isDefined)
    albumSave.enable(trackUnderWork.isDefined)

    //    startAtCheckBtn.enable(trackUnderWork.isDefined)
    //    startAtCheckBtn.select(startAtValue.isDefined)
    startAtEdit.setText(trackUnderWork.flatMap(_.startAt).orEmpty)
    startAtEdit.enable(trackUnderWork.isDefined)
    startAtSave.enable(trackUnderWork.isDefined)

    //    endAtCheckBtn.enable(trackUnderWork.isDefined)
    //    endAtCheckBtn.select(endAtValue.isDefined)
    endAtEdit.setText(trackUnderWork.flatMap(_.endAt).orEmpty)
    endAtEdit.enable(trackUnderWork.isDefined)
    endAtSave.enable(trackUnderWork.isDefined)

    //    fadeCheckBtn.enable(trackUnderWork.isDefined)
    //    fadeCheckBtn.select(fadeOutValue.isDefined)
    fadeEdit.setText(trackUnderWork.flatMap(_.fadeOutSeconds).map(_.toString).orEmpty)
    fadeEdit.enable(trackUnderWork.isDefined)
    fadeSave.enable(trackUnderWork.isDefined)

    //    val changeVolumeValue = trackUnderWork.flatMap(_.volumeChange).map(_.toString())
    //    volumeCheckBtn.enable(trackUnderWork.isDefined)
    //    volumeCheckBtn.select(changeVolumeValue.isDefined)
    //    volumeEdit.setText(changeVolumeValue.orEmpty)
    //    volumeEdit.enable(changeVolumeValue.isDefined)
    //    volumeSave.enable(changeVolumeValue.isDefined)

    val playlistsValue = trackUnderWork.map(_.playlists.toList.sorted.mkString(", "))
    playlistsEdit.setText(playlistsValue.orEmpty)
    playlistsEdit.enable(trackUnderWork.isDefined)
    playlistSave.enable(trackUnderWork.isDefined)

    deleteBtn.enable(trackUnderWork.isDefined)
    doneBtn.enable(!trackUnderWork.map(_.status).forall(_ == Final))
    draftBtn.enable(!trackUnderWork.map(_.status).forall(_ == Draft))
  }


  def getArtist(meta: YoutubeDLMeta) =
    meta.artist.filter(meta.fulltitle.contains).orElse(Some(meta.title)).map(normalizeName)

  def getTitle(meta: YoutubeDLMeta) = {
    if (meta.track.exists(meta.fulltitle.contains)) meta.track else {
      Some(meta.title).filter(meta.fulltitle.contains)
    }.orElse(Some(meta.title))
      .map(value => meta.artist.map(maybeArtist => value.replaceAllLiterally(maybeArtist, "")).getOrElse(value))
      .map(normalizeName)
  }

  private val storeUrl = Edit()
  private val storeBtn = Btn("Zapisz!").onClick(_ => {
    storeUrl.getText.trim.some.filter(_.nonEmpty).foreach { data =>
      val result = for {
        stored <- backend.storeUrl(data)
        meta <- MetaDataFetcher.getMetadata(data)
        updated1 <- getArtist(meta).map(backend.updateArtist(stored._id, _)).getOrElse(Right(stored))
        updated2 <- getTitle(meta).map(backend.updateTitle(stored._id, _)).getOrElse(Right(updated1))
      } yield updated2

      result.fold(
        err => logger.error("got error from storage", err),
        track => {
          trackUnderWork = track.some
          storeUrl.setText("")
          loadControls()
        }
      )
    }
  })

  private val nextDraft = Btn("Następny skic").onClick(_ => loadDraft())

  private def loadDraft() = {
    backend.nextDraft.map { maybeTrack =>
      trackUnderWork = maybeTrack
      println(maybeTrack.map(_.toString).getOrElse("brak szkiców"))
      loadControls()
    }.left.map { err =>
      println("loadDraft", err)
      err.printStackTrace()
    }
  }


  def load(in: ErrorOr[Track]): Either[Throwable, Unit] = in.map { updated =>
    trackUnderWork = updated.some
    loadControls()
  }

  add(VerticalLayout(
    HorizontalLayout(deleteBtn, storeUrl, storeBtn),
    nextDraft,
    HorizontalLayout(L("Link").withSizeRequest(labelBtnWidth, lineHeight), urlEdit, updateUrlBtn, openBtn, searchYTBtn),
    HorizontalLayout(L("Twórca").withSizeRequest(labelBtnWidth, lineHeight), artistEdit, artistSave),
    HorizontalLayout(L("Tytuł").withSizeRequest(labelBtnWidth, lineHeight), titleEdit, titleSave),
    HorizontalLayout(L("Album").withSizeRequest(labelBtnWidth, lineHeight), albumEdit, albumSave),
    HorizontalLayout(L("Opóżniony start").withSizeRequest(labelBtnWidth, lineHeight), /*startAtCheckBtn,*/ startAtEdit, startAtSave),
    HorizontalLayout(L("Wcześniejszy koniec").withSizeRequest(labelBtnWidth, lineHeight), /*endAtCheckBtn,*/ endAtEdit, endAtSave),
    HorizontalLayout(L("Wyciszanie").withSizeRequest(labelBtnWidth, lineHeight), /* fadeCheckBtn,*/ fadeEdit, fadeSave),
    HorizontalLayout(L("Playlisty").withSizeRequest(labelBtnWidth, lineHeight), /* fadeCheckBtn,*/ playlistsEdit, playlistSave),
    //    HorizontalLayout(L("Głośność"), volumeCheckBtn, volumeEdit, volumeSave),
    HorizontalLayout(draftBtn, doneBtn),
  ))
  setSizeRequest(width, height)

}
