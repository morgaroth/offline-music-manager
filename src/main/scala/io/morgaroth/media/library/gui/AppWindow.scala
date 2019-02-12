package io.morgaroth.media.library.gui

import java.util.UUID

import io.morgaroth.gnome.scala._
import cats.syntax.option._
import com.typesafe.scalalogging.LazyLogging
import io.morgaroth.media.library.ErrorOr
import io.morgaroth.media.library.storage.{Draft, Final, Track}
import org.gnome.gdk.{EventButton, EventKey, Keyval, MouseButton}
import org.gnome.gtk
import org.gnome.gtk.{CellRendererText, DataColumnReference, DataColumnString, Gtk, ListStore, TreeView, VBox, Widget}

import scala.sys.process._

class AppWindow(backend: GuiBackend) extends LazyLogging {
  var trackUnderWork: Option[Track] = None

  val w = new gtk.Window
  private val windowWidth = 1200
  private val windowHeight = 800
  w.setDefaultSize(windowWidth, windowHeight)
  val infoWindow = L("")

  val nextDraft = Btn("Następny skic").onClick(_ => {
    backend.nextDraft.map { maybeTrack =>
      maybeTrack.fold {
        trackUnderWork = none
        println("brak szkiców")
        infoWindow.setLabel("nie ma szkiców")
      }(x => {
        trackUnderWork = x.some
        println(x)
      })
      loadControls()
    }.left.map { err =>
      println(err)
      infoWindow.setLabel(s"ERROR: $err")
    }
  })

  val urlEdit = Edit().disabled
  val titleEdit = Edit().disabled
  val artistEdit = Edit().disabled

  val updateUrlBtn = Btn("zapisz").onClick(_ => trackUnderWork.foreach { track =>
    urlEdit.getText.some.filter(_ != track.url).map(backend.updateUrl(track.id, _)).map(load)
  }).disabled

  val openBtn = Btn("Otwórz!").onClick(_ => trackUnderWork.foreach {
    track => s"google-chrome ${track.url}".!
  }).disabled

  val artistSave = Btn("zapisz").onClick(_ => trackUnderWork.foreach { track =>
    artistEdit.getText.some.filter(_ != track.artist).map(backend.updateArtist(track.id, _)).map(load)
  }).disabled

  val titleSave = Btn("zapisz").onClick(_ => trackUnderWork.foreach { track =>
    titleEdit.getText.some.filter(_ != track.title).map(backend.updateTitle(track.id, _)).map(load)
  }).disabled

  val startAtCheckBtn = Checkbox("").disabled
  val endAtCheckBtn = Checkbox("").disabled
  val fadeCheckBtn = Checkbox("").disabled
  val volumeCheckBtn = Checkbox("").disabled
  val startAtEdit = Edit().disabled
  val endAtEdit = Edit().disabled
  val fadeEdit = Edit().disabled
  val volumeEdit = Edit().disabled
  val startAtSave = Btn("Zapisz").disabled
  val endAtSave = Btn("Zapisz").disabled
  val fadeSave = Btn("Zapisz").disabled
  val volumeSave = Btn("Zapisz").disabled
  val doneBtn = Btn("Zapisz jako gotowe").disabled
  val draftBtn = Btn("Zapisz jako szkic").disabled

  startAtCheckBtn.onToggle((x, _) => {
    startAtEdit.enable(x)
    startAtSave.enabled
    if (!x) startAtEdit.setText("")
  })

  endAtCheckBtn.onToggle((x, _) => {
    endAtEdit.enable(x)
    endAtSave.enabled
    if (!x) endAtEdit.setText("")
  })

  fadeCheckBtn.onToggle((x, _) => {
    fadeEdit.enable(x)
    fadeSave.enabled
    if (!x) fadeEdit.setText("")
  })

  volumeCheckBtn.onToggle((x, _) => {
    volumeEdit.enable(x)
    volumeSave.enabled
    if (!x) volumeEdit.setText("")
  })

  startAtSave.onClick { _ =>
    trackUnderWork.foreach { track =>
      val pData = startAtCheckBtn.getActive
      val v = Option(startAtEdit.getText).filter(_.matches("""^\d?\d:\d\d$"""))
      if (pData && v.isDefined) {
        logger.info(s"updating ${track.id}/startAt to $v")
        load(backend.updateStartAt(track.id, v))
      } else if (!pData) {
        logger.info(s"removing ${track.id}/startAt")
        load(backend.updateStartAt(track.id, None))
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
        load(backend.updateEndAt(track.id, v))
      } else if (!pData) {
        logger.info(s"removing ${track.id}/endAt")
        load(backend.updateEndAt(track.id, None))
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
        load(backend.updateFadeOutSeconds(track.id, v))
      } else if (!pData) {
        logger.info(s"removing ${track.id}/fadeOutSeconds")
        load(backend.updateFadeOutSeconds(track.id, None))
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
        val action = s"updating ${track.id}/volumeChange to $v"
        logger.info(action)
        load(backend.updateVolumeChange(track.id, v)).left.map(err => logger.warn(s"$action finished with error $err"))
      } else if (!pData) {
        val action = s"removing ${track.id}/volumeChange"
        logger.info(action)
        load(backend.updateVolumeChange(track.id, None)).left.map(err => logger.warn(s"$action finished with error $err"))
      } else {
        logger.warn(s"invalid data checked=$pData, value='$v'")
        Right(track)
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
    urlEdit.enable(trackUnderWork.isDefined)
    updateUrlBtn.enable(trackUnderWork.isDefined)
    openBtn.enable(trackUnderWork.isDefined)

    titleEdit.setText(trackUnderWork.orEmpty(_.title))
    titleEdit.enable(trackUnderWork.isDefined)
    titleSave.enable(trackUnderWork.isDefined)

    artistEdit.setText(trackUnderWork.orEmpty(_.artist))
    artistEdit.enable(trackUnderWork.isDefined)
    artistSave.enable(trackUnderWork.isDefined)

    val startAtValue = trackUnderWork.flatMap(_.startAt)
    startAtCheckBtn.enable(trackUnderWork.isDefined)
    startAtCheckBtn.select(startAtValue.isDefined)
    startAtEdit.setText(startAtValue.orEmpty)
    startAtEdit.enable(startAtValue.isDefined)
    startAtSave.enable(startAtValue.isDefined)

    val endAtValue = trackUnderWork.flatMap(_.endAt)
    endAtCheckBtn.enable(trackUnderWork.isDefined)
    endAtCheckBtn.select(endAtValue.isDefined)
    endAtEdit.setText(endAtValue.orEmpty)
    endAtEdit.enable(endAtValue.isDefined)
    endAtSave.enable(endAtValue.isDefined)

    val fadeOutValue = trackUnderWork.flatMap(_.fadeOutSeconds).map(_.toString)
    fadeCheckBtn.enable(trackUnderWork.isDefined)
    fadeCheckBtn.select(fadeOutValue.isDefined)
    fadeEdit.setText(fadeOutValue.orEmpty)
    fadeEdit.enable(fadeOutValue.isDefined)
    fadeSave.enable(fadeOutValue.isDefined)

//    val changeVolumeValue = trackUnderWork.flatMap(_.volumeChange).map(_.toString())
//    volumeCheckBtn.enable(trackUnderWork.isDefined)
//    volumeCheckBtn.select(changeVolumeValue.isDefined)
//    volumeEdit.setText(changeVolumeValue.orEmpty)
//    volumeEdit.enable(changeVolumeValue.isDefined)
//    volumeSave.enable(changeVolumeValue.isDefined)

    doneBtn.enable(!trackUnderWork.map(_.status).forall(_ == Final))
    draftBtn.enable(!trackUnderWork.map(_.status).forall(_ == Draft))
  }

  val storeUrl = Edit()
  val storeBtn = Btn("Zapisz!").onClick(_ => {
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

  w.add(VerticalLayout(
    HorizontalLayout(storeUrl, storeBtn),
    nextDraft,
    HorizontalLayout(L("Link"), urlEdit, updateUrlBtn, openBtn),
    HorizontalLayout(L("Twórca"), artistEdit, artistSave),
    HorizontalLayout(L("Tytuł"), titleEdit, titleSave),
    HorizontalLayout(L("Opóżniony start"), startAtCheckBtn, startAtEdit, startAtSave),
    HorizontalLayout(L("Wcześniejszy koniec"), endAtCheckBtn, endAtEdit, endAtSave),
    HorizontalLayout(L("Wyciszanie"), fadeCheckBtn, fadeEdit, fadeSave),
//    HorizontalLayout(L("Głośność"), volumeCheckBtn, volumeEdit, volumeSave),
    HorizontalLayout(draftBtn, doneBtn),
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

  def getSearchPane: VBox = {

    val pane = VerticalLayout()

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
    val input = Edit()

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