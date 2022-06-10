package io.morgaroth.media.library

import java.io.File

import scopt.OptionParser

import scala.util.Try

sealed trait MusicLibraryAction

case object Undefined extends MusicLibraryAction

case class Configuration(
                          action: MusicLibraryAction = Undefined,
                          destinationDir: File = new File(new File(new File(System.getProperty("user.home")), "music-library"), "all-music"),
                          cacheDir: File = new File(new File(new File(System.getProperty("user.home")), "music-library"), "cache"),
                          downloaderExec: String = "youtube-dl",
                          regex: String = ".*",
                          force: Boolean = false,
                          forceLevel: Int = 0,
                          debug: Boolean = false,
                        ) {
  def cacheLocation = cacheDir

  override def toString =
    s"""configuration:
    - action = $action
    - destinationDir = ${destinationDir.getAbsolutePath}
    - cacheDir = ${cacheDir.getAbsolutePath}
    - downloaderExec = $downloaderExec
    - filterRegex = $regex
    - force = $force
    - forceLevel = $forceLevel
    - debug = $debug
"""
}

object Args {

  val parser: OptionParser[Configuration] = new scopt.OptionParser[Configuration]("Media Library Manager") {
    head("media library manager", "0.1.0")
    opt[File]('d', "destination-dir")
      .valueName("<file>")
      .action((x, c) => c.copy(destinationDir = x))
      .text("place where files should be put")

    opt[String]("downloader-exec")
      .valueName("<path>")
      .action((x, c) => c.copy(downloaderExec = x))
      .text("program used to fetch data")

    opt[String]("filter")
      .valueName("<regex>")
      .validate(x => Try(x.r).toEither.left.map(_.toString).map(_ => ()))
      .action((x, c) => c.copy(regex = x))
      .text("program used to fetch data")

    opt[Int]("force")
      .action((x, c) => c.copy(forceLevel = x))
      .text("level of force, 1 - only extract final from encoded, 2 - doreencoding")

    opt[Boolean]("debug")
      .action((x, c) => c.copy(debug = x))
      .text("debug mode")
  }

  def apply(args: Seq[String]): Option[Configuration] = {
    parser.parse(args, Configuration())
  }
}