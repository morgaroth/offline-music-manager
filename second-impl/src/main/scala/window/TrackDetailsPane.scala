package io.gitlab.mateuszjaje.offlinemusiclibrary
package window

import io.morgaroth.media.library.storage.{Track, TracksStorage}
import scalafx.Includes._
import scalafx.event.ActionEvent
import scalafx.scene.control.{Button, Label, TextArea, TextField}
import zio.Task

import java.util.UUID
import scala.concurrent.Future


class TrackDetailsPane(backend: ZGuiBackend) extends scalafx.scene.layout.GridPane {

  val descArea = new TextArea {
    wrapText = true
    prefWidth = 750
    prefHeight = 450
  }

  var trackUnderWork: Option[Track] = None

  val healthLabel = new Label("Health:")
  val healthDisplay = new Label("100/100")
  val linkEdit = new TextField {}
  val titleEdit = new TextField {}
  val albumEdit = new TextField {}
  val startAtEdit = new TextField {}
  val endAtEdit = new TextField {}
  val fadeEdit = new TextField {}

  val linkSave = new Button("Zapisz")
  val titleSave = new Button {
    text = "Zapisz"
    onAction = (event: ActionEvent) => {
      trackUnderWork.foreach { track =>
        Some(normalizeName(titleEdit.getText)).filter(_ != track.title).map {
          title => zio.Runtime.default.unsafeRun(backend.updateTitle(track._id, title))
        }
      }
    }
  }
  val albumSave = new Button("Zapisz")
  val startAtSave = new Button("Zapisz")
  val endAtSave = new Button("Zapisz")
  val fadeSave = new Button("Zapisz")

  List(
    List(new Label("URL"), linkEdit, linkSave),
    List(new Label("Tytuł"), titleEdit, titleSave),
    List(new Label("Album"), albumEdit, albumSave),
    List(new Label("Opróźniony start"), startAtEdit, startAtSave),
    List(new Label("Wcześniejszy koniec"), endAtEdit, endAtSave),
    List(new Label("Wyciszanie"), fadeEdit, fadeSave),
  ).zipWithIndex.foreach {
    case (cells, row) =>
      cells.zipWithIndex.foreach {
        case (cell, col) =>
          add(cell, col, row)
      }
  }

  private val nameStripped = """^[\s-/]*(.+?)[\s-/]*$""".r

  private def normalizeName(rawValue: String) = {
    rawValue
      .replace("Official Video", "")
      .replace("official video", "")
      .replace("official audio", "")
      .replace("Lyrics", "")
      .replace("()", "")
      .trim match {
      case nameStripped(name) => name
      case "" => ""
    }
  }

}

trait ZGuiBackend {
  def updateArtist(id: UUID, artist: String): Task[Track]

  def updateAlbum(id: UUID, album: String): Task[Track]

  def updateTitle(id: UUID, title: String): Task[Track]
}

class ZGuiBackendLive(db: TracksStorage[Future]) extends ZGuiBackend {
  def updateArtist(id: UUID, artist: String) = {
    Task.fromFuture(_ => db.updateArtist(id, artist)).flatMap(Task.fromEither(_))
  }

  def updateAlbum(id: UUID, album: String): Task[Track] = {
    Task.fromFuture(_ => db.updateAlbum(id, album)).flatMap(Task.fromEither(_))
  }

  def updateTitle(id: UUID, title: String): Task[Track] =
    Task.fromFuture(_ => db.updateTitle(id, title)).flatMap(Task.fromEither(_))
}