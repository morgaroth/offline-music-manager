package io.morgaroth.media.library.gui

import io.morgaroth.media.library.storage.Track
import javafx.collections.{FXCollections, ObservableList}
import scalafx.Includes.*
import scalafx.beans.property.StringProperty
import scalafx.geometry.Insets
import scalafx.scene.control.{Button, Label, TableColumn, TableRow, TableView, TextField}
import scalafx.scene.input.{KeyCode, KeyEvent, MouseEvent}
import scalafx.scene.layout.{HBox, Priority, VBox}
import zio.Runtime

/** Full-window browse tab: search bar + paginated table showing all track details. */
class BrowseTab(backend: GuiBackend, runtime: Runtime[Any], onTrackSelected: Track => Unit) extends VBox:
  spacing = 0
  padding = Insets(10)

  private val searchField = new TextField:
    promptText = "Szukaj po artyście, tytule, albumie, URL..."
    hgrow = Priority.Always

  private val searchButton = new Button("Szukaj")
  private val prevButton = new Button("<")
  private val nextButton = new Button(">")
  private val pageLabel = new Label("1")
  private val countLabel = new Label("")

  private var currentPage = 1

  private val tracks: ObservableList[Track] = FXCollections.observableArrayList[Track]()

  private val tableView = new TableView[Track](tracks):
    vgrow = Priority.Always

    columns ++= Seq(
      new TableColumn[Track, String]:
        text = "Twórca"
        prefWidth = 160
        cellValueFactory = row => StringProperty(row.value.artist)
      ,
      new TableColumn[Track, String]:
        text = "Tytuł"
        prefWidth = 220
        cellValueFactory = row => StringProperty(row.value.title)
      ,
      new TableColumn[Track, String]:
        text = "Album"
        prefWidth = 130
        cellValueFactory = row => StringProperty(row.value.album)
      ,
      new TableColumn[Track, String]:
        text = "Status"
        prefWidth = 70
        cellValueFactory = row => StringProperty(row.value.status.dbRepr)
      ,
      new TableColumn[Track, String]:
        text = "URL"
        prefWidth = 200
        cellValueFactory = row => StringProperty(row.value.url)
      ,
      new TableColumn[Track, String]:
        text = "Start"
        prefWidth = 55
        cellValueFactory = row => StringProperty(row.value.startAt.getOrElse(""))
      ,
      new TableColumn[Track, String]:
        text = "Koniec"
        prefWidth = 55
        cellValueFactory = row => StringProperty(row.value.endAt.getOrElse(""))
      ,
      new TableColumn[Track, String]:
        text = "Fade"
        prefWidth = 45
        cellValueFactory = row => StringProperty(row.value.fadeOutSeconds.map(_.toString).getOrElse(""))
      ,
      new TableColumn[Track, String]:
        text = "Playlisty"
        prefWidth = 120
        cellValueFactory = row => StringProperty(row.value.playlists.toList.sorted.mkString(", "))
      ,
      new TableColumn[Track, String]:
        text = "Dodano"
        prefWidth = 90
        cellValueFactory = row => StringProperty(row.value.createdAtLocal.toLocalDate.toString)
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
    padding = Insets(0, 0, 8, 0)
    children = Seq(searchField, searchButton, prevButton, pageLabel, nextButton, countLabel)

  children = Seq(searchBar, tableView)
  VBox.setVgrow(tableView, Priority.Always)

  // Load first page on init
  loadPage()

  private def doSearch(): Unit =
    currentPage = 1
    pageLabel.text = "1"
    loadPage()

  private def loadPage(): Unit =
    val text = searchField.text.value
    FxBridge.run(runtime)(backend.search(text, currentPage))(
      results =>
        tracks.clear()
        results.foreach(tracks.add)
        countLabel.text = s"  (${results.size} wyników)"
      ,
      err =>
        println(s"[BrowseTab] search error: ${err.getMessage}")
        err.printStackTrace()
    )
