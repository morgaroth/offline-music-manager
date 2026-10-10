package io.morgaroth.media.library.http

import zio.{ULayer, ZLayer}

import java.io.File

/** Runtime configuration for the HTTP server + fetcher.
  *
  * Sourced through [[OptionsSource]] so it works both as an HA add-on (values
  * from `/data/options.json`) and standalone (`MUSIC_LIBRARY_*` env vars).
  *
  * Output goes to the `output_dir` option / `MUSIC_LIBRARY_OUTPUT_DIR` env, or a
  * default under the home dir. (NFS mounting was removed; a new storage design
  * will replace it later.)
  */
case class ServerConfig(
  port: Int,
  outputDir: File,
  cacheDir: File,
  downloaderExec: String,
  cookiesFile: Option[String],
)

object ServerConfig:
  private def defaultBase: File =
    File(File(System.getProperty("user.home")), "music-library")

  def fromOptions(opts: OptionsSource): ServerConfig =
    val port = opts.int("http_port", "MUSIC_LIBRARY_HTTP_PORT", 57381)
    // In the container the downloader is bundled at a known location, so this is
    // not a user-facing option. The env var stays only as an escape hatch.
    val downloader = opts.str("", "MUSIC_LIBRARY_DOWNLOADER").getOrElse("yt-dlp")

    // Output dir: `output_dir` option / MUSIC_LIBRARY_OUTPUT_DIR env, else default.
    val outputDir = opts.str("output_dir", "MUSIC_LIBRARY_OUTPUT_DIR")
      .map(File(_))
      .getOrElse(File(defaultBase, "all-music"))

    val cacheDir = opts.str("", "MUSIC_LIBRARY_CACHE_DIR")
      .map(File(_))
      .getOrElse(File(defaultBase, "cache"))

    // Optional yt-dlp cookies file (Netscape format). When unset, downloads run
    // anonymously — fine for most public videos, needed for restricted ones.
    val cookiesFile = opts.str("cookies_file", "MUSIC_LIBRARY_COOKIES_FILE")

    ServerConfig(port, outputDir, cacheDir, downloader, cookiesFile)

  val fromEnv: ServerConfig = fromOptions(OptionsSource.load())

  val live: ULayer[ServerConfig] = ZLayer.succeed(fromEnv)
