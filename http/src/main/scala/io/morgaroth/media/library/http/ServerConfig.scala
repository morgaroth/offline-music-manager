package io.morgaroth.media.library.http

import zio.{ULayer, ZLayer}

import java.io.File

/** Runtime configuration for the HTTP server + fetcher.
  *
  * Sourced through [[OptionsSource]] so it works both as an HA add-on (values
  * from `/data/options.json`) and standalone (`MEDIA_LIBRARY_*` env vars).
  *
  * Output goes to the `output_dir` option / `MEDIA_LIBRARY_OUTPUT_DIR` env, or a
  * default under the home dir. (NFS mounting was removed; a new storage design
  * will replace it later.)
  */
case class ServerConfig(
  port: Int,
  outputDir: File,
  videoOutputDir: File,
  cacheDir: File,
  downloaderExec: String,
  cookiesFile: Option[String],
)

object ServerConfig:
  private def defaultBase: File =
    File(File(System.getProperty("user.home")), "music-library")

  def fromOptions(opts: OptionsSource): ServerConfig =
    val port = opts.int("http_port", "MEDIA_LIBRARY_HTTP_PORT", 57381)
    // In the container the downloader is bundled at a known location, so this is
    // not a user-facing option. The env var stays only as an escape hatch.
    val downloader = opts.str("", "MEDIA_LIBRARY_DOWNLOADER").getOrElse("yt-dlp")

    // Music output: `music_output_path` option / MEDIA_LIBRARY_MUSIC_OUTPUT_PATH env.
    val outputDir = opts.str("music_output_path", "MEDIA_LIBRARY_MUSIC_OUTPUT_PATH")
      .map(File(_))
      .getOrElse(File(defaultBase, "all-music"))

    // Video output: `video_output_path` option / MEDIA_LIBRARY_VIDEO_OUTPUT_PATH env
    // (e.g. a Jellyfin mount, kept separate from music).
    val videoOutputDir = opts.str("video_output_path", "MEDIA_LIBRARY_VIDEO_OUTPUT_PATH")
      .map(File(_))
      .getOrElse(File(defaultBase, "videos"))

    // Cache: `cache_path` option / MEDIA_LIBRARY_CACHE_PATH env; else /data/cache
    // when the persistent /data volume exists; else home default.
    val cacheDir = opts.str("cache_path", "MEDIA_LIBRARY_CACHE_PATH")
      .map(File(_))
      .getOrElse:
        val dataVol = File("/data")
        if dataVol.isDirectory then File(dataVol, "cache")
        else File(defaultBase, "cache")

    // Optional yt-dlp cookies file (Netscape format). When unset, downloads run
    // anonymously — fine for most public videos, needed for restricted ones.
    val cookiesFile = opts.str("cookies_file", "MEDIA_LIBRARY_COOKIES_FILE")

    ServerConfig(port, outputDir, videoOutputDir, cacheDir, downloader, cookiesFile)

  val fromEnv: ServerConfig = fromOptions(OptionsSource.load())

  val live: ULayer[ServerConfig] = ZLayer.succeed(fromEnv)
