package io.morgaroth.media.library.gui

import io.morgaroth.media.library.storage.{Track, ZioTracksStorageService}
import javafx.collections.{FXCollections, ObservableList}
import scalafx.Includes.*
import scalafx.beans.property.StringProperty
import scalafx.geometry.Insets
import scalafx.scene.control.*
import scalafx.scene.input.{KeyCode, KeyEvent, MouseEvent}
import scalafx.scene.layout.{HBox, Priority, VBox}
import zio.Runtime

/** Manage tab: track details editing pane on top, search/selection table on bottom.
  * Includes a "Pobierz" (pull/download) button to fetch a single song.
  */
class ManageTab(backend: GuiBackend, storage: ZioTracksStorageService, runtime: Runtime[Any]) extends SplitPane:
  orientation = scalafx.geometry.Orientation.Vertical
  dividerPositions = 0.5

  private val trackDetailsPane = TrackDetailsPane(backend, storage, runtime)

  // --- Search panel at the bottom ---
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
    )

  tableView.onMouseClicked = (event: MouseEvent) =>
    if event.clickCount == 2 then
      val selected = tableView.selectionModel.value.getSelectedItem
      if selected != null then trackDetailsPane.load(selected)

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
    padding = Insets(5)
    children = Seq(searchField, searchButton, prevButton, pageLabel, nextButton)

  private val searchPanel = new VBox:
    spacing = 0
    padding = Insets(5)
    children = Seq(searchBar, tableView)
    VBox.setVgrow(tableView, Priority.Always)

  items.addAll(trackDetailsPane, searchPanel)

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
      ,
      err =>
        println(s"[ManageTab] search error: ${err.getMessage}")
        err.printStackTrace()
    )

  /** Load a track into the details pane (called externally, e.g. from BrowseTab). */
  def loadTrack(track: Track): Unit =
    trackDetailsPane.load(track)
