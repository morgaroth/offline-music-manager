package io.morgaroth.media.library.gui

import io.morgaroth.media.library.storage.Track
import javafx.collections.{FXCollections, ObservableList}
import scalafx.Includes.*
import scalafx.beans.property.StringProperty
import scalafx.geometry.Insets
import scalafx.scene.control.{Button, Label, TableColumn, TableView, TextField}
import scalafx.scene.input.{KeyCode, KeyEvent, MouseEvent}
import scalafx.scene.layout.{HBox, Priority, VBox}
import zio.*

import java.util.UUID

class SearchPane(backend: GuiBackend, runtime: Runtime[Any], onTrackSelected: Track => Unit) extends VBox:
  spacing = 8
  padding = Insets(10)

  private val searchField = new TextField:
    promptText = "Szukaj..."
    hgrow = Priority.Always

  private val searchButton = new Button("Szukaj")
  private val prevButton = new Button("<")
  private val nextButton = new Button(">")
  private val pageLabel = new Label("1")

  private var currentPage = 1

  private val tracks: ObservableList[Track] = FXCollections.observableArrayList[Track]()

  private val tableView = new TableView[Track](tracks):
    prefHeight = 300
    vgrow = Priority.Always

    columns ++= Seq(
      new TableColumn[Track, String]:
        text = "Twórca"
        prefWidth = 150
        cellValueFactory = row => StringProperty(row.value.artist)
      ,
      new TableColumn[Track, String]:
        text = "Tytuł"
        prefWidth = 200
        cellValueFactory = row => StringProperty(row.value.title)
      ,
      new TableColumn[Track, String]:
        text = "Album"
        prefWidth = 120
        cellValueFactory = row => StringProperty(row.value.album)
      ,
      new TableColumn[Track, String]:
        text = "Status"
        prefWidth = 70
        cellValueFactory = row => StringProperty(row.value.status.dbRepr)
      ,
      new TableColumn[Track, String]:
        text = "Start"
        prefWidth = 60
        cellValueFactory = row => StringProperty(row.value.startAt.getOrElse(""))
      ,
      new TableColumn[Track, String]:
        text = "Koniec"
        prefWidth = 60
        cellValueFactory = row => StringProperty(row.value.endAt.getOrElse(""))
      ,
      new TableColumn[Track, String]:
        text = "Fade"
        prefWidth = 50
        cellValueFactory = row => StringProperty(row.value.fadeOutSeconds.map(_.toString).getOrElse(""))
      ,
    )

  tableView.onMouseClicked = (event: MouseEvent) =>
    if event.clickCount == 2 then
      val selected = tableView.selectionModel.value.getSelectedItem
      if selected != null then onTrackSelected(selected)

  searchField.onKeyPressed = (event: KeyEvent) =>
    if event.code == KeyCode.Enter then doSearch()

  searchButton.onAction = _ => doSearch()

  prevButton.onAction = _ =>
    currentPage = Math.max(1, currentPage - 1)
    pageLabel.text = currentPage.toString
    loadPage()

  nextButton.onAction = _ =>
    currentPage += 1
    pageLabel.text = currentPage.toString
    loadPage()

  private val searchBar = new HBox:
    spacing = 5
    children = Seq(searchField, searchButton, prevButton, pageLabel, nextButton)

  children = Seq(searchBar, tableView)

  private def doSearch(): Unit =
    currentPage = 1
    pageLabel.text = "1"
    loadPage()

  private def loadPage(): Unit =
    val text = searchField.text.value
    Unsafe.unsafe { implicit u =>
      runtime.unsafe.fork(
        FxBridge.runOnFx(backend.search(text, currentPage))(results =>
          tracks.clear()
          results.foreach(tracks.add)
        )
      )
    }
