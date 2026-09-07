package io.morgaroth.media.library.gui

import com.typesafe.config.ConfigFactory
import io.morgaroth.media.library.storage.{DatabaseConfig, DataSourceLive, TracksStoragePostgres, ZioTracksStorageService}
import scalafx.application.JFXApp3
import scalafx.scene.Scene
import scalafx.scene.control.{Tab, TabPane}
import scalafx.scene.layout.Priority
import zio.*

/** Main ScalaFX application with ZIO 2 runtime.
  *
  * Three tabs:
  *   - Manage: edit track details + search/select + pull single song
  *   - Run: launch fetcher with 3 concurrent downloads + logs + progress
  *   - Browse: full-window search table with all columns
  */
object MusicLibraryApp extends JFXApp3:

  private val appLayer: ZLayer[Any, Throwable, GuiBackend & ZioTracksStorageService] =
    val configLayer = ZLayer.fromZIO:
      ZIO.attempt(DatabaseConfig.fromTypesafeConfig(ConfigFactory.load()))
    val storageLayer = configLayer >>> DataSourceLive.layer >>> TracksStoragePostgres.layer
    storageLayer >+> GuiBackend.live

  override def start(): Unit =
    val runtime: Runtime[GuiBackend & ZioTracksStorageService] =
      Unsafe.unsafe { implicit u =>
        Runtime.unsafe.fromLayer(appLayer)
      }

    val backend: GuiBackend =
      Unsafe.unsafe { implicit u =>
        runtime.unsafe.run(ZIO.service[GuiBackend]).getOrThrowFiberFailure()
      }

    val storage: ZioTracksStorageService =
      Unsafe.unsafe { implicit u =>
        runtime.unsafe.run(ZIO.service[ZioTracksStorageService]).getOrThrowFiberFailure()
      }

    val manageTab = ManageTab(backend, storage, runtime)
    val runTab = RunTab(backend, storage, runtime)

    lazy val browseTab: BrowseTab = BrowseTab(backend, runtime, track => {
      manageTab.loadTrack(track)
      tabPane.selectionModel.value.select(0)
    })

    lazy val tabPane: TabPane = new TabPane:
      tabs = Seq(
        new Tab:
          text = "Manage"
          content = manageTab
          closable = false
        ,
        new Tab:
          text = "Run"
          content = runTab
          closable = false
        ,
        new Tab:
          text = "Browse"
          content = browseTab
          closable = false
        ,
      )

    stage = new JFXApp3.PrimaryStage:
      title = "Music Library Manager"
      width = 1100
      height = 850
      scene = new Scene:
        content = tabPane
        tabPane.prefWidth <== this.width
        tabPane.prefHeight <== this.height

  override def stopApp(): Unit =
    java.lang.System.exit(0)
