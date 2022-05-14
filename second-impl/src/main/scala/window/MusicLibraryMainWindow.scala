package io.gitlab.mateuszjaje.offlinemusiclibrary
package window

import io.morgaroth.media.library.storage.MongoRepo
import javafx.geometry.{Insets, VPos}
import javafx.scene.control.ListView
import scalafx.Includes._
import scalafx.application.JFXApp3
import scalafx.scene.Scene
import scalafx.scene.control.Label
import scalafx.scene.layout.GridPane

object MusicLibraryMainWindow extends JFXApp3 {
  private val windowWidth = 1000
  private val windowHeight = 800

    val storage = MongoRepo.AllTracks()

  override def start(): Unit = {
    val manaLabel = new Label("mana:")
    val manaDisplay = new Label("100/100")
    val firstPane = new TrackDetailsPane(new ZGuiBackendLive(storage))

    GridPane.setConstraints(firstPane, 0, 0)

    GridPane.setConstraints(manaLabel, 1, 1)
    GridPane.setValignment(manaLabel, VPos.TOP)

    GridPane.setConstraints(manaDisplay, 2, 1)
    GridPane.setValignment(manaDisplay, VPos.TOP)

    val listView = new ListView[String]()
    listView.getItems.addAll("Action 1", "Action 2", "Action 3", "Action 4")
    listView.setPrefWidth(260)
    listView.setPrefHeight(150)
    GridPane.setValignment(listView, VPos.BASELINE)
    GridPane.setConstraints(listView, 0, 2)

    stage = new JFXApp3.PrimaryStage {
      title.value = "Music Manager"
      width = windowWidth
      height = windowHeight

      scene = new Scene {
        content = new GridPane {
          padding = new Insets(10, 10, 10, 10)
          vgap = 10
          hgap = 10
          children.addAll(firstPane, manaLabel, manaDisplay, listView)
        }
      }
    }
  }
}