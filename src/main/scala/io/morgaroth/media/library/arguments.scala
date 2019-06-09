package io.morgaroth.media.library

import java.io.File

case class Configuration(
                          destinationDir: File = new File(new File(System.getProperty("user.home")), "music-library"),
                          downloaderExec: String = "youtube-dl",
                          force: Boolean = false,
                          forceLevel: Int = 0,
                          debug: Boolean= false,
                        ) {
  def cacheLocation = new File(destinationDir, "cache")

  override def toString =
    s"""configuration:
    - destinationDir = ${destinationDir.getAbsolutePath}
    - downloaderExec = $downloaderExec
    - force = $force
    - forceLevel = $forceLevel
"""
}

object Args {

  val parser = new scopt.OptionParser[Configuration]("Media Library Manager") {
    head("media library manager", "0.1.0")
    opt[File]('d', "destination")
      .valueName("<file>")
      .action((x, c) => c.copy(destinationDir = x))
      .text("place where files should be put")

    opt[String]("downloader-exec")
      .valueName("<path>")
      .action((x, c) => c.copy(downloaderExec = x))
      .text("program used to fetch data")

    opt[Int]("force")
      .action((x, c) => c.copy(forceLevel = x))
      .text("level of force, 1 - only extract final from encoded, 2 - doreencoding")
  }

  def apply(args: Array[String]) = {
    parser.parse(args, Configuration())
  }
}