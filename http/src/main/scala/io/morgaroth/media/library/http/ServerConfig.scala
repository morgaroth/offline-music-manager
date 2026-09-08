package io.morgaroth.media.library.http

import zio.{ULayer, ZLayer}

import java.io.File

/** Runtime configuration for the HTTP server, sourced from environment
  * variables so it can be driven by a Home Assistant add-on's options.
  *
  *   - MUSIC_LIBRARY_HTTP_PORT   bind port (default 8080)
  *   - MUSIC_LIBRARY_OUTPUT_DIR  where finished mp3s are written; point this at
  *     an NTFS mount (e.g. /share/navidrome/music) to feed Navidrome directly
  *   - MUSIC_LIBRARY_CACHE_DIR   scratch dir for downloads/conversion
  *   - MUSIC_LIBRARY_DOWNLOADER  downloader executable (default yt-dlp)
  */
case class ServerConfig(
  port: Int,
  outputDir: File,
  cacheDir: File,
  downloaderExec: String,
)

object ServerConfig:
  private def envStr(key: String): Option[String] =
    sys.env.get(key).map(_.trim).filter(_.nonEmpty)

  private def defaultBase: File =
    File(File(System.getProperty("user.home")), "music-library")

  val fromEnv: ServerConfig =
    val port = envStr("MUSIC_LIBRARY_HTTP_PORT").flatMap(_.toIntOption).getOrElse(8080)
    val outputDir = envStr("MUSIC_LIBRARY_OUTPUT_DIR")
      .map(File(_))
      .getOrElse(File(defaultBase, "all-music"))
    val cacheDir = envStr("MUSIC_LIBRARY_CACHE_DIR")
      .map(File(_))
      .getOrElse(File(defaultBase, "cache"))
    val downloader = envStr("MUSIC_LIBRARY_DOWNLOADER").getOrElse("yt-dlp")
    ServerConfig(port, outputDir, cacheDir, downloader)

  val live: ULayer[ServerConfig] = ZLayer.succeed(fromEnv)
