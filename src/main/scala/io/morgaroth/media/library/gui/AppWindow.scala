package io.morgaroth.media.library.gui

import java.util.UUID

import cats.syntax.option._
import com.typesafe.scalalogging.LazyLogging
import io.morgaroth.gnome.scala._
import io.morgaroth.media.library.ErrorOr
import io.morgaroth.media.library.storage.{Deleted, Draft, Final, Track}
import org.gnome.gdk.{EventButton, EventKey, Keyval, MouseButton}
import org.gnome.gtk
import org.gnome.gtk.{CellRendererText, DataColumnReference, DataColumnString, Gtk, ListStore, TreeView, VBox, Widget}

import scala.sys.process._

class AppWindow(backend: GuiBackend) extends LazyLogging {
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
    rawValue.trim match {
      case nameStripped(name) => name
      case "" => ""
    }
  }

  val w = new gtk.Window
  private val windowWidth = 1200
  private val windowHeight = 800
  w.setDefaultSize(windowWidth, windowHeight)
  private val infoWindow = L("")

  private val nextDraft = Btn("Następny skic").onClick(_ => loadDraft())

  private def loadDraft() = {
    backend.nextDraft.map { maybeTrack =>
      trackUnderWork = maybeTrack
      println(maybeTrack.map(_.toString).getOrElse("brak szkiców"))
      loadControls()
    }.left.map { err =>
      println(err)
      infoWindow.setLabel(s"ERROR: $err")
    }
  }

  private val urlEdit = Edit().disabled
  private val titleEdit = Edit().disabled
  private val artistEdit = Edit().disabled
  private val albumEdit = Edit().disabled

  private val updateUrlBtn = Btn("zapisz").onClick(_ => trackUnderWork.foreach { track =>
    urlEdit.getText.trim.some.filter(_ != track.url).map(backend.updateUrl(track.id, _)).map(load)
  }).disabled

  private val openBtn = Btn("Otwórz!").onClick(_ => trackUnderWork.foreach {
    track => s"google-chrome ${track.url}".!
  }).disabled

  private val searchYTBtn = Btn("Szukaj w YT!").onClick(_ => trackUnderWork.foreach { track =>
    s"google-chrome ${track.searchUrl}".!
  }).disabled

  private val artistSave = Btn("zapisz").onClick(_ => trackUnderWork.foreach { track =>
    normalizeName(artistEdit.getText).some.filter(_ != track.artist).map { artist =>
      logger.info(s"updating ${track.id}/artist to $artist")
      backend.updateArtist(track.id, artist)
    }.map(load)
  }).disabled

  private val albumSave = Btn("zapisz").onClick(_ => trackUnderWork.foreach { track =>
    normalizeName(albumEdit.getText).some.filter(_ != track.album).map { album =>
      logger.info(s"updating ${track.id}/album to $album")
      backend.updateAlbum(track.id, album)
    }.map(load)
  }).disabled

  private val titleSave = Btn("zapisz").onClick(_ => trackUnderWork.foreach { track =>
    normalizeName(titleEdit.getText).some.filter(_ != track.title).map { title =>
      logger.info(s"updating ${track.id}/title to $title")
      backend.updateTitle(track.id, title)
    }.map(load)
  }).disabled

  //  private val startAtCheckBtn = Checkbox("").disabled
  //  private val endAtCheckBtn = Checkbox("").disabled
  //  private val fadeCheckBtn = Checkbox("").disabled
  //  private val volumeCheckBtn = Checkbox("").disabled
  private val startAtEdit = Edit().disabled
  private val endAtEdit = Edit().disabled
  private val fadeEdit = Edit().disabled
  private val volumeEdit = Edit().disabled
  private val startAtSave = Btn("Zapisz").disabled
  private val endAtSave = Btn("Zapisz").disabled
  private val fadeSave = Btn("Zapisz").disabled
  private val volumeSave = Btn("Zapisz").disabled
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
          logger.info(s"updating ${track.id}/startAt to $validValue")
          load(backend.updateStartAt(track.id, Some(validValue)))
        case "" =>
          logger.info(s"removing ${track.id}/startAt")
          load(backend.updateStartAt(track.id, None))
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
          logger.info(s"updating ${track.id}/endAt to $validValue")
          load(backend.updateEndAt(track.id, Some(validValue)))
        case "" =>
          logger.info(s"removing ${track.id}/endAt")
          load(backend.updateEndAt(track.id, None))
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
          logger.info(s"updating ${track.id}/fadeOutSeconds to $validValue")
          load(backend.updateFadeOutSeconds(track.id, Some(validValue.toInt)))
        case "" =>
          logger.info(s"removing ${track.id}/fadeOutSeconds")
          load(backend.updateFadeOutSeconds(track.id, None))
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
    logger.info(s"updating ${track.id}/status to ${Deleted.dbRepr}")
    backend.updateStatus(track.id, Deleted)
  }.map(_ => loadDraft()))

  doneBtn.onClick(_ => trackUnderWork.map { track =>
    logger.info(s"updating ${track.id}/status to ${Final.dbRepr}")
    backend.updateStatus(track.id, Final)
  }.map(load))

  draftBtn.onClick(_ => trackUnderWork.map { track =>
    logger.info(s"updating ${track.id}/status to ${Draft.dbRepr}")
    backend.updateStatus(track.id, Draft)
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

    deleteBtn.enable(trackUnderWork.isDefined)
    doneBtn.enable(!trackUnderWork.map(_.status).forall(_ == Final))
    draftBtn.enable(!trackUnderWork.map(_.status).forall(_ == Draft))
  }

  private val storeUrl = Edit()
  private val storeBtn = Btn("Zapisz!").onClick(_ => {
    storeUrl.getText.trim.some.filter(_.nonEmpty).foreach { data =>
      backend.storeUrl(data).fold(
        err => infoWindow.setLabel(s"ERROR: $err"),
        track => {
          trackUnderWork = track.some
          storeUrl.setText("")
          loadControls()
        }
      )
    }
  })

  w.add(VerticalLayout(
    HorizontalLayout(storeUrl, storeBtn),
    nextDraft,
    HorizontalLayout(HorizontalLayout(L("Link"), urlEdit), HorizontalLayout(updateUrlBtn, openBtn, searchYTBtn)),
    HorizontalLayout(L("Twórca"), artistEdit, artistSave),
    HorizontalLayout(L("Tytuł"), titleEdit, titleSave),
    HorizontalLayout(L("Album"), albumEdit, albumSave),
    HorizontalLayout(L("Opóżniony start"), /*startAtCheckBtn,*/ startAtEdit, startAtSave),
    HorizontalLayout(L("Wcześniejszy koniec"), /*endAtCheckBtn,*/ endAtEdit, endAtSave),
    HorizontalLayout(L("Wyciszanie"), /* fadeCheckBtn,*/ fadeEdit, fadeSave),
    //    HorizontalLayout(L("Głośność"), volumeCheckBtn, volumeEdit, volumeSave),
    HorizontalLayout(draftBtn, doneBtn),
    getSearchPane,
    HorizontalLayout(deleteBtn),
  ))
  w.setTitle("Music Manager")
  w.showAll()

  w.closeOnDeleteEvent()

  def show() {
    Gtk.main()
  }

  def load(in: ErrorOr[Track]): Either[Throwable, Unit] = in.map { updated =>
    trackUnderWork = updated.some
    loadControls()
  }

  def getSearchPane: VBox = {

    val pane = VerticalLayout()

    val artistColumn = new DataColumnString()
    val titleColumn = new DataColumnString()
    val albumColumn = new DataColumnString()
    val urlColumn = new DataColumnString()
    val statusColumn = new DataColumnString()
    val startAtColumn = new DataColumnString()
    val endAtColumn = new DataColumnString()
    val fadeColumn = new DataColumnString()
    val volumeColumn = new DataColumnString()
    val idColumn = new DataColumnReference[UUID]()

    val resultsStore = new ListStore(Array(artistColumn, titleColumn, albumColumn, statusColumn, startAtColumn, endAtColumn, fadeColumn, volumeColumn, urlColumn, idColumn))
    val input = Edit()

    def loadResults(in: Vector[Track]) {
      logger.info(s"search result is $in")
      resultsStore.clear()
      in.foreach { t =>
        val r = resultsStore.appendRow()
        resultsStore.setValue(r, artistColumn, t.artist)
        resultsStore.setValue(r, titleColumn, t.title)
        resultsStore.setValue(r, albumColumn, t.album)
        resultsStore.setValue(r, urlColumn, t.url)
        resultsStore.setValue(r, statusColumn, t.status.dbRepr)
        resultsStore.setValue(r, startAtColumn, t.startAt.getOrElse(""))
        resultsStore.setValue(r, endAtColumn, t.endAt.getOrElse(""))
        resultsStore.setValue(r, fadeColumn, t.fadeOutSeconds.map(_.toString).getOrElse(""))
        resultsStore.setValue(r, volumeColumn, t.volumeChange.map(_.toString).getOrElse(""))
        resultsStore.setValue(r, idColumn, t.id)
      }
    }

    val view = new TreeView(resultsStore)
    view.setSizeRequest(windowHeight, 300)

    view.connect(new Widget.ButtonReleaseEvent {
      override def onButtonReleaseEvent(widget: Widget, eventButton: EventButton) = {
        if (eventButton.getButton == MouseButton.RIGHT) {
          val path = view.getPathAtPos(eventButton.getX.toInt, eventButton.getY.toInt)
          val row = view.getModel.getIter(path)
          val id = view.getModel.getValue(row, idColumn)
          load(backend.getById(id))
          true
        } else false
      }
    })

    def col(name: String, col: DataColumnString) {
      val column = view.appendColumn()
      column.setTitle(name)
      val renderer = new CellRendererText(column)
      renderer.setText(col)
    }

    col("Twórca", artistColumn)
    col("Tytuł", titleColumn)
    col("Album", albumColumn)
    col("Stan", statusColumn)
    col("Start", startAtColumn)
    col("Koniec", endAtColumn)
    col("Fade", fadeColumn)
    col("Volume", volumeColumn)
    col("Url", urlColumn)

    val currentPage = Edit("1")
    val next = Btn(">")
    val prev = Btn("<")

    next.onClick { _ =>
      val np = currentPage.getText.toInt + 1
      currentPage.setText(np.toString)
      loadPage()
    }

    prev.onClick { _ =>
      val np = Math.max(1, currentPage.getText.toInt - 1)
      currentPage.setText(np.toString)
      loadPage()
    }

    def loadPage() = {
      val page = currentPage.getText.toInt
      backend.search(input.getText, page).map(loadResults)
    }

    val exec = Btn("Szukaj").onClick { _ =>
      currentPage.setText("1")
      loadPage()
    }

    input.connect(new Widget.KeyReleaseEvent {
      override def onKeyReleaseEvent(widget: Widget, eventKey: EventKey) = {
        if (eventKey.getKeyval == Keyval.Return) {
          currentPage.setText("1")
          loadPage()
          true
        } else false
      }
    })

    pane.add(HorizontalLayout(input, exec, HorizontalLayout(prev, currentPage, next)))
    pane.add(view)
    pane
  }
}