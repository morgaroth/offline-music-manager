package io.morgaroth.media.library

import scopt.OParser

import java.io.File
import scala.util.Try

case class Configuration(
  action: String = "undefined",
  destinationDir: File = File(File(File(System.getProperty("user.home")), "music-library"), "all-music"),
  cacheDir: File = File(File(File(System.getProperty("user.home")), "music-library"), "cache"),
  downloaderExec: String = "yt-dlp",
  regex: String = ".*",
  force: Boolean = false,
  forceLevel: Int = 0,
  debug: Boolean = false,
  cookiesFile: Option[String] = None,
):
  def cacheLocation: File = cacheDir

  /** yt-dlp cookie args: a `--cookies <file>` pair when configured, else empty
    * (anonymous). Replaces the old hardcoded `--cookies-from-browser chrome`,
    * which cannot work in a headless container.
    */
  def cookieArgs: Seq[String] =
    cookiesFile.filter(_.nonEmpty).toSeq.flatMap(f => Seq("--cookies", f))

  override def toString: String =
    s"""configuration:
       |  - action = $action
       |  - destinationDir = ${destinationDir.getAbsolutePath}
       |  - cacheDir = ${cacheDir.getAbsolutePath}
       |  - downloaderExec = $downloaderExec
       |  - cookiesFile = ${cookiesFile.getOrElse("<none>")}
       |  - filterRegex = $regex
       |  - force = $force
       |  - forceLevel = $forceLevel
       |  - debug = $debug
       |""".stripMargin

object Args:
  private val builder = OParser.builder[Configuration]
  import builder.*

  private val parser = OParser.sequence(
    programName("Media Library Manager"),
    head("media library manager", "0.2.0"),
    opt[File]('d', "destination-dir")
      .valueName("<file>")
      .action((x, c) => c.copy(destinationDir = x))
      .text("place where files should be put"),
    opt[String]("downloader-exec")
      .valueName("<path>")
      .action((x, c) => c.copy(downloaderExec = x))
      .text("program used to fetch data"),
    opt[String]("filter")
      .valueName("<regex>")
      .validate(x => Try(x.r).toEither.left.map(_.toString).map(_ => ()))
      .action((x, c) => c.copy(regex = x))
      .text("regex to filter tracks"),
    opt[Int]("force")
      .action((x, c) => c.copy(forceLevel = x))
      .text("level of force, 1 - only extract final from encoded, 2 - do reencoding"),
    opt[Unit]("debug")
      .action((_, c) => c.copy(debug = true))
      .text("debug mode"),
    arg[String]("<action>")
      .optional()
      .action((x, c) => c.copy(action = x))
      .text("action to perform: fetch, gui"),
  )

  def apply(args: Seq[String]): Option[Configuration] =
    OParser.parse(parser, args, Configuration())
