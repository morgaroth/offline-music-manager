package io.morgaroth.media.library.gui.panes

import java.util.UUID

import com.typesafe.scalalogging.LazyLogging
import io.morgaroth.gnome.scala.{Btn, Edit, HorizontalLayout, _}
import io.morgaroth.media.library.gui.GuiBackend
import io.morgaroth.media.library.storage.Track
import org.gnome.gdk.{EventButton, EventKey, Keyval, MouseButton}
import org.gnome.gtk
import org.gnome.gtk.{CellRendererText, DataColumnReference, DataColumnString, ListStore, TreeView, Widget}

trait SearchPaneActionsListener {
  def onTrackRightClick(trackId: UUID): Unit
}

class SearchPane(
                  width: Int,
                  height: Int,
                  backend: GuiBackend,
                  actionsListener: SearchPaneActionsListener
                ) extends gtk.VBox(false, 1) with LazyLogging {

  private val headerHeight = 35

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
  view.setSizeRequest(width, height - headerHeight)

  view.connect(new Widget.ButtonReleaseEvent {
    override def onButtonReleaseEvent(widget: Widget, eventButton: EventButton) = {
      if (eventButton.getButton == MouseButton.RIGHT) {
        val path = view.getPathAtPos(eventButton.getX.toInt, eventButton.getY.toInt)
        val row = view.getModel.getIter(path)
        val id = view.getModel.getValue(row, idColumn)
        actionsListener.onTrackRightClick(id)
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

  add(HorizontalLayout(input, exec, HorizontalLayout(prev, currentPage, next)).withSizeRequest(width, headerHeight))
  add(view)
}
