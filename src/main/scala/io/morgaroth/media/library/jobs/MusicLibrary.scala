package io.morgaroth.media.library.jobs

object MusicLibrary {
  def main(args: Array[String]): Unit = {
    args.headOption match {
      case Some("fetch" | "boot" | "work") =>
        new Boot().main(args.drop(1))
      case Some("gui") | None =>
        GUIApp.main(args.drop(1))
      case e =>
        System.err.println(s"Unknown command '$e', valid are: fetch, boot, work, gui, `no command`.")
        sys.exit(-1)
    }
  }
}
