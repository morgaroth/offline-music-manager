package io.morgaroth.media.library.gui

import io.morgaroth.media.library.storage.{Track, TrackStatus}
import scalafx.Includes.*
import scalafx.geometry.Insets
import scalafx.scene.control.{Button, Label, TextField}
import scalafx.scene.layout.{GridPane, HBox, Priority, VBox}
import zio.*

import java.util.UUID

class TrackDetailsPane(backend: GuiBackend, runtime: Runtime[Any]) extends VBox:
  spacing = 8
  padding = Insets(10)

  private var trackUnderWork: Option[Track] = None

  private val urlField = new TextField { promptText = "URL"; disable = true; hgrow = Priority.Always }
  private val titleField = new TextField { promptText = "Tytuł"; disable = true; hgrow = Priority.Always }
  private val artistField = new TextField { promptText = "Twórca"; disable = true; hgrow = Priority.Always }
  private val albumField = new TextField { promptText = "Album"; disable = true; hgrow = Priority.Always }
  private val startAtField = new TextField { promptText = "Start"; disable = true; prefWidth = 80 }
  private val endAtField = new TextField { promptText = "Koniec"; disable = true; prefWidth = 80 }
  private val fadeField = new TextField { promptText = "Fade (s)"; disable = true; prefWidth = 80 }
  private val playlistsField = new TextField { promptText = "Playlisty (comma-sep)"; disable = true; hgrow = Priority.Always }

  private val storeUrlField = new TextField { promptText = "Nowy URL do dodania..."; hgrow = Priority.Always }

  private def saveBtn(label: String)(action: => Unit) = new Button(label):
    onAction = _ => action
    disable = true
    prefWidth = 70

  private val urlSave = saveBtn("Zapisz") { saveField(urlField, _.url, (id, v) => backend.updateUrl(id, v)) }
  private val titleSave = saveBtn("Zapisz") { saveField(titleField, _.title, (id, v) => backend.updateTitle(id, normalizeName(v))) }
  private val artistSave = saveBtn("Zapisz") { saveField(artistField, _.artist, (id, v) => backend.updateArtist(id, normalizeName(v))) }
  private val albumSave = saveBtn("Zapisz") { saveField(albumField, _.album, (id, v) => backend.updateAlbum(id, normalizeName(v))) }

  private val startAtSave = saveBtn("Zapisz"):
    trackUnderWork.foreach: track =>
      val value = Option(startAtField.text.value).map(_.trim).filter(_.nonEmpty)
      runEffect(backend.updateStartAt(track._id, value.map(normalizeTimeValue)))

  private val endAtSave = saveBtn("Zapisz"):
    trackUnderWork.foreach: track =>
      val value = Option(endAtField.text.value).map(_.trim).filter(_.nonEmpty)
      runEffect(backend.updateEndAt(track._id, value.map(normalizeTimeValue)))

  private val fadeSave = saveBtn("Zapisz"):
    trackUnderWork.foreach: track =>
      val value = Option(fadeField.text.value).map(_.trim).filter(_.nonEmpty).flatMap(_.toIntOption)
      runEffect(backend.updateFadeOutSeconds(track._id, value))

  private val playlistsSave = saveBtn("Zapisz"):
    trackUnderWork.foreach: track =>
      val value = playlistsField.text.value.trim.split(",").map(_.trim).filter(_.nonEmpty).toSet
      runEffect(backend.updatePlaylists(track._id, value))

  private val deleteBtn = new Button("Usuń"):
    disable = true
    onAction = _ => trackUnderWork.foreach(t => runEffect(backend.updateStatus(t._id, TrackStatus.Deleted)))

  private val doneBtn = new Button("Gotowe"):
    disable = true
    onAction = _ => trackUnderWork.foreach(t => runEffect(backend.updateStatus(t._id, TrackStatus.Final)))

  private val draftBtn = new Button("Szkic"):
    disable = true
    onAction = _ => trackUnderWork.foreach(t => runEffect(backend.updateStatus(t._id, TrackStatus.Draft)))

  private val nextDraftBtn = new Button("Następny szkic"):
    onAction = _ =>
      Unsafe.unsafe { implicit u =>
        runtime.unsafe.fork(
          FxBridge.runOnFx(backend.nextDraft):
            case Some(track) => load(track)
            case None => clearControls()
        )
      }

  private val storeBtn = new Button("Dodaj"):
    onAction = _ =>
      val url = storeUrlField.text.value.trim
      if url.nonEmpty then
        Unsafe.unsafe { implicit u =>
          runtime.unsafe.fork(
            FxBridge.runOnFx(backend.storeUrl(url))(track =>
              storeUrlField.text = ""
              load(track)
            )
          )
        }

  private val openInBrowserBtn = new Button("Otwórz"):
    disable = true
    onAction = _ => trackUnderWork.foreach: track =>
      java.lang.Runtime.getRuntime.exec(Array("xdg-open", track.url))

  // Layout
  private val grid = new GridPane:
    hgap = 8
    vgap = 6
    padding = Insets(5)

  private def addRow(row: Int, label: String, field: TextField, btn: Button): Unit =
    grid.add(new Label(label) { prefWidth = 140 }, 0, row)
    grid.add(field, 1, row)
    grid.add(btn, 2, row)
    GridPane.setHgrow(field, Priority.Always)

  addRow(0, "URL", urlField, urlSave)
  addRow(1, "Twórca", artistField, artistSave)
  addRow(2, "Tytuł", titleField, titleSave)
  addRow(3, "Album", albumField, albumSave)
  addRow(4, "Start", startAtField, startAtSave)
  addRow(5, "Koniec", endAtField, endAtSave)
  addRow(6, "Fade (s)", fadeField, fadeSave)
  addRow(7, "Playlisty", playlistsField, playlistsSave)

  private val actionsBar = new HBox:
    spacing = 8
    children = Seq(deleteBtn, draftBtn, doneBtn, openInBrowserBtn)

  private val addBar = new HBox:
    spacing = 8
    children = Seq(storeUrlField, storeBtn, nextDraftBtn)

  children = Seq(addBar, grid, actionsBar)

  def load(track: Track): Unit =
    trackUnderWork = Some(track)
    urlField.text = track.url
    titleField.text = track.title
    artistField.text = track.artist
    albumField.text = track.album
    startAtField.text = track.startAt.getOrElse("")
    endAtField.text = track.endAt.getOrElse("")
    fadeField.text = track.fadeOutSeconds.map(_.toString).getOrElse("")
    playlistsField.text = track.playlists.toList.sorted.mkString(", ")
    setControlsEnabled(true)

  private def clearControls(): Unit =
    trackUnderWork = None
    urlField.text = ""
    titleField.text = ""
    artistField.text = ""
    albumField.text = ""
    startAtField.text = ""
    endAtField.text = ""
    fadeField.text = ""
    playlistsField.text = ""
    setControlsEnabled(false)

  private def setControlsEnabled(enabled: Boolean): Unit =
    val disabled = !enabled
    Seq(urlField, titleField, artistField, albumField, startAtField, endAtField, fadeField, playlistsField).foreach(_.disable = disabled)
    Seq(urlSave, titleSave, artistSave, albumSave, startAtSave, endAtSave, fadeSave, playlistsSave, deleteBtn, doneBtn, draftBtn, openInBrowserBtn).foreach(_.disable = disabled)

  private def saveField(field: TextField, getter: Track => String, updater: (UUID, String) => Task[Track]): Unit =
    trackUnderWork.foreach: track =>
      val newValue = field.text.value.trim
      if newValue != getter(track) then
        runEffect(updater(track._id, newValue))

  private def runEffect(effect: Task[Track]): Unit =
    Unsafe.unsafe { implicit u =>
      runtime.unsafe.fork(
        FxBridge.runOnFx(effect)(track => load(track))
      )
    }

  private val nameStripped = """^[\s\-/]*(.+?)[\s\-/]*$""".r

  private def normalizeName(rawValue: String): String =
    rawValue
      .replace("Official Video", "")
      .replace("official video", "")
      .replace("official audio", "")
      .replace("Lyrics", "")
      .replace("()", "")
      .trim match
      case nameStripped(name) => name
      case "" => ""
      case other => other

  private def normalizeTimeValue(rawValue: String): String =
    rawValue.trim.replaceAll("[;,]", ":") match
      case v if v.matches("""\s*\d\s*""") => s"0:0${v.trim}"
      case v if v.matches("""\s*\d\d\s*""") => s"0:${v.trim}"
      case v => v
