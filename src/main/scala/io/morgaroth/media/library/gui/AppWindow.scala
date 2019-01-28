package io.morgaroth.media.library.gui

import java.util.UUID

import cats.syntax.option._
import com.typesafe.scalalogging.LazyLogging
import io.morgaroth.media.library.ErrorOr
import io.morgaroth.media.library.storage.{Draft, Final, Track}
import org.gnome.gdk.{EventButton, EventKey, Keyval, MouseButton}
import org.gnome.gtk
import org.gnome.gtk.{CellRendererText, DataColumnReference, DataColumnString, Gtk, ListStore, TreeView, Widget}

import scala.sys.process._

class AppWindow(backend: GuiBackend) extends RichGtk with LazyLogging {

  var trackUnderWork: Option[Track] = None

  val w = new gtk.Window
  private val windowWidth = 1200
  private val windowHeight = 800
  w.setDefaultSize(windowWidth, windowHeight)
  val infoWindow = L("")

  val nextDraft = Btn("Następny skic", _ => {
    backend.nextDraft.map { maybeTrack =>
      maybeTrack.map { x =>
        trackUnderWork = x.some
        println(x)
      }.getOrElse {
        trackUnderWork = none
        println("brak szkiców")
        infoWindow.setLabel("nie ma szkiców")
      }
      loadControls()
    }.left.map { err =>
      println(err)
      infoWindow.setLabel(s"ERROR: $err")
    }
  })

  val urlEdit = Entry().disable
  val titleEdit = Entry().disable
  val artistEdit = Entry().disable

  val updateUrlBtn = Btn("zapisz", _ => trackUnderWork.foreach { track =>
    urlEdit.getText.some.filter(_ != track.url).map(backend.updateUrl(track.id, _)).map(load)
  }).disabled

  val openBtn = Btn("Otwórz!", _ => trackUnderWork.foreach {
    track => s"google-chrome ${track.url}".!
  }).disabled

  val artistSave = Btn("zapisz", _ => trackUnderWork.foreach { track =>
    artistEdit.getText.some.filter(_ != track.artist).map(backend.updateArtist(track.id, _)).map(load)
  }).disabled

  val titleSave = Btn("zapisz", _ => trackUnderWork.foreach { track =>
    titleEdit.getText.some.filter(_ != track.title).map(backend.updateTitle(track.id, _)).map(load)
  }).disabled

  val startAtCheckBtn = Checkbox("").disabled
  val endAtCheckBtn = Checkbox("").disabled
  val fadeCheckBtn = Checkbox("").disabled
  val volumeCheckBtn = Checkbox("").disabled
  val startAtEdit = Entry().sensitive(false)
  val endAtEdit = Entry().sensitive(false)
  val fadeEdit = Entry().sensitive(false)
  val volumeEdit = Entry().sensitive(false)
  val startAtSave = Btn("Zapisz").disabled
  val endAtSave = Btn("Zapisz").disabled
  val fadeSave = Btn("Zapisz").disabled
  val volumeSave = Btn("Zapisz").disabled
  val doneBtn = Btn("Zapisz jako gotowe").disabled
  val draftBtn = Btn("Zapisz jako szkic").disabled

  startAtCheckBtn.onToggle((x, _) => {
    startAtEdit.enabled(x)
    startAtSave.enable
    if (!x) startAtEdit.setText("")
  })

  endAtCheckBtn.onToggle((x, _) => {
    endAtEdit.enabled(x)
    endAtSave.enable
    if (!x) endAtEdit.setText("")
  })

  fadeCheckBtn.onToggle((x, _) => {
    fadeEdit.enabled(x)
    fadeSave.enable
    if (!x) fadeEdit.setText("")
  })

  volumeCheckBtn.onToggle((x, _) => {
    volumeEdit.enabled(x)
    volumeSave.enable
    if (!x) volumeEdit.setText("")
  })

  startAtSave.onClick { _ =>
    trackUnderWork.foreach { track =>
      val pData = startAtCheckBtn.getActive
      val v = Option(startAtEdit.getText).filter(_.matches("""^\d?\d:\d\d$"""))
      if (pData && v.isDefined) {
        logger.info(s"updating ${track.id}/startAt to $v")
        backend.updateStartAt(track.id, v)
      } else if (!pData) {
        logger.info(s"removing ${track.id}/startAt")
        backend.updateStartAt(track.id, None)
      } else {
        logger.warn(s"invalid data checked=$pData, value='$v'")
      }
    }
  }

  endAtSave.onClick { _ =>
    trackUnderWork.foreach { track =>
      val pData = endAtCheckBtn.getActive
      val v = Option(endAtEdit.getText).filter(_.matches("""^\d?\d:\d\d$"""))
      if (pData && v.isDefined) {
        logger.info(s"updating ${track.id}/endAt to $v")
        backend.updateEndAt(track.id, v)
      } else if (!pData) {
        logger.info(s"removing ${track.id}/endAt")
        backend.updateEndAt(track.id, None)
      } else {
        logger.warn(s"invalid data checked=$pData, value='$v'")
      }
    }
  }

  fadeSave.onClick { _ =>
    trackUnderWork.foreach { track =>
      val pData = fadeCheckBtn.getActive
      val v = Option(fadeEdit.getText).filter(_.matches("""^\d+$""")).map(_.toInt)
      if (pData && v.isDefined) {
        logger.info(s"updating ${track.id}/fadeOutSeconds to $v")
        backend.updateFadeOutSeconds(track.id, v)
      } else if (!pData) {
        logger.info(s"removing ${track.id}/fadeOutSeconds")
        backend.updateFadeOutSeconds(track.id, None)
      } else {
        logger.warn(s"invalid data checked=$pData, value='$v'")
      }
    }
  }

  volumeSave.onClick { _ =>
    trackUnderWork.foreach { track =>
      val pData = volumeCheckBtn.getActive
      val v = Option(volumeEdit.getText).filter(_.matches("""^\d+(\.\d+)?$""")).map(BigDecimal(_))
      if (pData && v.isDefined) {
        logger.info(s"updating ${track.id}/volumeChange to $v")
        backend.updateVolumeChange(track.id, v)
      } else if (!pData) {
        logger.info(s"removing ${track.id}/volumeChange")
        backend.updateVolumeChange(track.id, None)
      } else {
        logger.warn(s"invalid data checked=$pData, value='$v'")
      }
    }
  }

  doneBtn.onClick(_ => trackUnderWork.map { track =>
    backend.updateStatus(track.id, Final)
  }.map(load))

  draftBtn.onClick(_ => trackUnderWork.map { track =>
    backend.updateStatus(track.id, Draft)
  }.map(load))

  def loadControls() = {
    urlEdit.setText(trackUnderWork.orEmpty(_.url))
    urlEdit.enabled(trackUnderWork.isDefined)
    updateUrlBtn.enabled(trackUnderWork.isDefined)
    openBtn.enabled(trackUnderWork.isDefined)

    titleEdit.setText(trackUnderWork.orEmpty(_.title))
    titleEdit.enabled(trackUnderWork.isDefined)
    titleSave.enabled(trackUnderWork.isDefined)

    artistEdit.setText(trackUnderWork.orEmpty(_.artist))
    artistEdit.enabled(trackUnderWork.isDefined)
    artistSave.enabled(trackUnderWork.isDefined)

    val startAtValue = trackUnderWork.flatMap(_.startAt)
    startAtCheckBtn.enabled(trackUnderWork.isDefined)
    startAtCheckBtn.select(startAtValue.isDefined)
    startAtEdit.setText(startAtValue.orEmpty)
    startAtEdit.enabled(startAtValue.isDefined)
    startAtSave.enabled(startAtValue.isDefined)

    val endAtValue = trackUnderWork.flatMap(_.endAt)
    endAtCheckBtn.enabled(trackUnderWork.isDefined)
    endAtCheckBtn.select(endAtValue.isDefined)
    endAtEdit.setText(endAtValue.orEmpty)
    endAtEdit.enabled(endAtValue.isDefined)
    endAtSave.enabled(endAtValue.isDefined)

    val fadeOutValue = trackUnderWork.flatMap(_.fadeOutSeconds)
    fadeCheckBtn.enabled(trackUnderWork.isDefined)
    fadeCheckBtn.select(fadeOutValue.isDefined)
    fadeEdit.setText(fadeOutValue.map(_.toString).orEmpty)
    fadeEdit.enabled(fadeOutValue.isDefined)
    fadeSave.enabled(fadeOutValue.isDefined)

    val changeVolumeValue = trackUnderWork.flatMap(_.volumeChange)
    volumeCheckBtn.enabled(trackUnderWork.isDefined)
    volumeCheckBtn.select(changeVolumeValue.isDefined)
    volumeEdit.setText(changeVolumeValue.map(_.toString).orEmpty)
    volumeEdit.enabled(changeVolumeValue.isDefined)
    volumeSave.enabled(changeVolumeValue.isDefined)

    doneBtn.enabled(!trackUnderWork.map(_.status).forall(_ == Final))
    draftBtn.enabled(!trackUnderWork.map(_.status).forall(_ == Draft))
  }

  val storeUrl = Entry()
  val storeBtn = Btn("Zapisz!", _ => {
    storeUrl.getText.some.filter(_.nonEmpty).foreach { data =>
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

  w.add(VerticalLayout(8,
    HorizontalLayout(2, storeUrl, storeBtn),
    nextDraft,
    HorizontalLayout(3, L("Link"), urlEdit, updateUrlBtn, openBtn),
    HorizontalLayout(3, L("Twórca"), artistEdit, artistSave),
    HorizontalLayout(2, L("Tytuł"), titleEdit, titleSave),
    HorizontalLayout(4, L("Opóżniony start"), startAtCheckBtn, startAtEdit, startAtSave),
    HorizontalLayout(4, L("Wcześniejszy koniec"), endAtCheckBtn, endAtEdit, endAtSave),
    HorizontalLayout(4, L("Wyciszanie"), fadeCheckBtn, fadeEdit, fadeSave),
    HorizontalLayout(4, L("Głośność"), volumeCheckBtn, volumeEdit, volumeSave),
    HorizontalLayout(10, draftBtn, doneBtn),
    getSearchPane
  ))
  w.setTitle("Music Manager")
  w.showAll()

  w.closeOnDeleteEvent()

  def show() = {
    Gtk.main()
  }

  def load(in: ErrorOr[Track]): Either[Throwable, Unit] = in.map { updated =>
    trackUnderWork = updated.some
    loadControls()
  }

  def getSearchPane = {

    val pane = VerticalLayout(1)

    val artistColumn = new DataColumnString()
    val titleColumn = new DataColumnString()
    val urlColumn = new DataColumnString()
    val statusColumn = new DataColumnString()
    val startAtColumn = new DataColumnString()
    val endAtColumn = new DataColumnString()
    val fadeColumn = new DataColumnString()
    val volumeColumn = new DataColumnString()
    val idColumn = new DataColumnReference[UUID]()

    val resultsStore = new ListStore(Array(artistColumn, titleColumn, statusColumn, startAtColumn, endAtColumn, fadeColumn, volumeColumn, urlColumn, idColumn))
    val input = Entry()

    def loadResults(in: Vector[Track]) = {
      logger.info(s"search result is $in")
      resultsStore.clear()
      in.foreach { t =>
        val r = resultsStore.appendRow()
        resultsStore.setValue(r, artistColumn, t.artist)
        resultsStore.setValue(r, titleColumn, t.title)
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

    def col(name: String, col: DataColumnString) = {
      val column = view.appendColumn()
      column.setTitle(name)
      val renderer = new CellRendererText(column)
      renderer.setText(col)
    }

    col("Twórca", artistColumn)
    col("Tytuł", titleColumn)
    col("Stan", statusColumn)
    col("Start", startAtColumn)
    col("Koniec", endAtColumn)
    col("Fade", fadeColumn)
    col("Volume", volumeColumn)
    col("Url", urlColumn)

    val currentPage = Entry("1")
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

    val exec = Btn("Szukaj", _ => {
      currentPage.setText("1")
      loadPage()
    })

    input.connect(new Widget.KeyReleaseEvent {
      override def onKeyReleaseEvent(widget: Widget, eventKey: EventKey) = {
        if (eventKey.getKeyval == Keyval.Return) {
          currentPage.setText("1")
          loadPage()
          true
        } else false
      }
    })

    pane.add(HorizontalLayout(1, input, exec, HorizontalLayout(1, prev, currentPage, next)))
    pane.add(view)
    pane
  }
}