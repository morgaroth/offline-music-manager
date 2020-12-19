package io.morgaroth.media.library.jobs

import io.morgaroth.media.library.storage.TracksStorage

import scala.concurrent.Future

class BaseMusicLibrary(storage: TracksStorage[Future]) {
  def main(args: Array[String]): Unit = {
    args.headOption match {
      case Some("fetch" | "boot" | "work") =>
        new Boot(storage).main(args.drop(1))
      case Some("gui") | None =>
        new BaseGUIApp(storage).main()
      case e =>
        System.err.println(s"Unknown command '$e', valid are: fetch, boot, work, gui, `no command`.")
        sys.exit(-1)
    }
  }
}
