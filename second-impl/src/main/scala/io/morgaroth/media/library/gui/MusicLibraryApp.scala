package io.morgaroth.media.library.gui

import com.typesafe.config.ConfigFactory
import io.morgaroth.media.library.storage.{DatabaseConfig, DataSourceLive, TracksStoragePostgres}
import scalafx.application.JFXApp3
import scalafx.geometry.Insets
import scalafx.scene.Scene
import scalafx.scene.control.SplitPane
import scalafx.scene.layout.{Priority, VBox}
import zio.*

/** Main ScalaFX application with ZIO 2 runtime for the music library GUI.
  *
  * Architecture:
  *   - ZIO Runtime is created at startup and shared with UI components
  *   - UI components dispatch ZIO effects via FxBridge (runs on ZIO fibers,
  *     delivers results back on the JavaFX thread)
  *   - GuiBackend is the ZIO service layer connecting UI to PostgreSQL storage
  */
object MusicLibraryApp extends JFXApp3:

  private val appLayer: ZLayer[Any, Throwable, GuiBackend] =
    val configLayer = ZLayer.fromZIO:
      ZIO.attempt(DatabaseConfig.fromTypesafeConfig(ConfigFactory.load()))
    configLayer >>> DataSourceLive.layer >>> TracksStoragePostgres.layer >>> GuiBackend.live

  override def start(): Unit =
    val runtime: Runtime[GuiBackend] =
      Unsafe.unsafe { implicit u =>
        Runtime.unsafe.fromLayer(appLayer)
      }

    val backend: GuiBackend =
      Unsafe.unsafe { implicit u =>
        runtime.unsafe.run(ZIO.service[GuiBackend]).getOrThrowFiberFailure()
      }

    val trackDetailsPane = TrackDetailsPane(backend, runtime)
    val searchPane = SearchPane(backend, runtime, track => trackDetailsPane.load(track))

    stage = new JFXApp3.PrimaryStage:
      title = "Music Library Manager"
      width = 1000
      height = 800
      scene = new Scene:
        content = new SplitPane:
          orientation = scalafx.geometry.Orientation.Vertical
          dividerPositions = 0.45
          items.addAll(
            new VBox:
              padding = Insets(0)
              children = Seq(trackDetailsPane)
              VBox.setVgrow(trackDetailsPane, Priority.Always)
            ,
            new VBox:
              padding = Insets(0)
              children = Seq(searchPane)
              VBox.setVgrow(searchPane, Priority.Always)
          )

  override def stopApp(): Unit =
    java.lang.System.exit(0)
