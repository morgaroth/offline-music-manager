package io.gitlab.mateuszjaje.offlinemusiclibrary
package window

import javafx.geometry.{Insets, VPos}
import javafx.scene.control.ListView
import scalafx.Includes._
import scalafx.application.JFXApp3
import scalafx.scene.Scene
import scalafx.scene.control.{Label, TextArea}
import scalafx.scene.layout.GridPane

object HelloStageDemo extends JFXApp3 {
  private val windowWidth = 1000
  private val windowHeight = 800

  override def start(): Unit = {
    stage = new JFXApp3.PrimaryStage {

      val descArea = new TextArea {
        wrapText = true
        prefWidth = 750
        prefHeight = 450
      }

      val healthLabel = new Label("Health:")
      val manaLabel = new Label("mana:")
      val healthDisplay = new Label("100/100")
      val manaDisplay = new Label("100/100")

      GridPane.setConstraints(descArea, 0, 0)

      GridPane.setConstraints(healthLabel, 1, 0)
      GridPane.setValignment(healthLabel, VPos.TOP)

      GridPane.setConstraints(healthDisplay, 2, 0)
      GridPane.setValignment(healthDisplay, VPos.TOP)

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

      title.value = "Hello Stage"
      width = windowWidth
      height = windowHeight

      scene = new Scene {
        //        width = windowWidth
        //        height = windowHeight
        content = new GridPane {
          padding = new Insets(10, 10, 10, 10)
          vgap = 10
          hgap = 10
          children.addAll(descArea, healthLabel, healthDisplay, manaLabel, manaDisplay, listView)
        }
      }
    }
  }
}