package io.morgaroth.media.library.gui.windows

import java.util.UUID

import cats.syntax.either._
import com.typesafe.scalalogging.LazyLogging
import io.gitlab.mateuszjaje.gnome.scala._
import io.morgaroth.media.library.gui.GuiBackend
import io.morgaroth.media.library.gui.panes.{SearchPane, SearchPaneActionsListener, TrackDetailsPane}
import org.gnome.gtk
import org.gnome.gtk.Gtk

class AppWindow(backend: GuiBackend) extends LazyLogging {

  private val windowWidth = 1000
  private val windowHeight = 800

  val w = new gtk.Window
  w.setDefaultSize(windowWidth, windowHeight)
  w.closeOnDeleteEvent()
  w.setTitle("Music Manager")

  val trackDetailsPane = new TrackDetailsPane(windowWidth, 500, backend)

  val searchPaneListener = new SearchPaneActionsListener {
    override def onTrackRightClick(trackId: UUID): Unit = {
      trackDetailsPane.load(backend.getById(trackId)).leftMap { error =>
        logger.error("searchPaneListener.onTrackRightClick failed because", error)
      }
    }
  }

  val searchPane = new SearchPane(windowWidth, 300, backend, searchPaneListener)

  w.add(VerticalLayout(
    trackDetailsPane,
    searchPane,
  ))
  w.showAll()

  def show() {
    Gtk.main()
  }
}