package io.morgaroth.media.library.jobs

import io.morgaroth.media.library.storage.MongoRepo

object DeprecatedGUIApp {
  def main(args: Array[String]): Unit = {
    val storage = MongoRepo.fromArgs()
    val gui = new BaseGUIApp(storage)
    gui.main()
  }
}
