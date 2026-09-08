package io.morgaroth.media.library.http

import zio.{ULayer, ZLayer}

import java.io.File

/** Runtime configuration for the HTTP server + fetcher.
  *
  * Sourced through [[OptionsSource]] so it works both as an HA add-on (values
  * from `/data/options.json`) and standalone (`MUSIC_LIBRARY_*` env vars).
  *
  * When NFS is enabled, the entrypoint mounts the export at [[nfsMountRoot]] and
  * output goes to `<mount>/<output_subdir>`; otherwise output goes to
  * `MUSIC_LIBRARY_OUTPUT_DIR` (env) or a default under the home dir.
  */
case class ServerConfig(
  port: Int,
  outputDir: File,
  cacheDir: File,
  downloaderExec: String,
  cookiesFile: Option[String],
)

object ServerConfig:
  val nfsMountRoot = "/mnt/music"

  private def defaultBase: File =
    File(File(System.getProperty("user.home")), "music-library")

  def fromOptions(opts: OptionsSource): ServerConfig =
    val port = opts.int("http_port", "MUSIC_LIBRARY_HTTP_PORT", 8080)
    // In the container the downloader is bundled at a known location, so this is
    // not a user-facing option. The env var stays only as an escape hatch.
    val downloader = opts.str("", "MUSIC_LIBRARY_DOWNLOADER").getOrElse("yt-dlp")

    // Output dir resolution:
    //  - explicit env MUSIC_LIBRARY_OUTPUT_DIR wins (standalone),
    //  - else if NFS enabled, <mount>/<output_subdir>,
    //  - else <home>/music-library/all-music.
    val outputDir =
      opts.str("", "MUSIC_LIBRARY_OUTPUT_DIR") match
        case Some(explicit) => File(explicit)
        case None =>
          val subdir = opts.string("output_subdir", "MUSIC_LIBRARY_OUTPUT_SUBDIR", "offline-music-manager")
          if opts.bool("nfs_enabled", "MUSIC_LIBRARY_NFS_ENABLED", false) then
            File(File(nfsMountRoot), subdir)
          else
            File(defaultBase, "all-music")

    val cacheDir = opts.str("", "MUSIC_LIBRARY_CACHE_DIR")
      .map(File(_))
      .getOrElse(File(defaultBase, "cache"))

    // Optional yt-dlp cookies file (Netscape format). When unset, downloads run
    // anonymously — fine for most public videos, needed for restricted ones.
    val cookiesFile = opts.str("cookies_file", "MUSIC_LIBRARY_COOKIES_FILE")

    ServerConfig(port, outputDir, cacheDir, downloader, cookiesFile)

  val fromEnv: ServerConfig = fromOptions(OptionsSource.load())

  val live: ULayer[ServerConfig] = ZLayer.succeed(fromEnv)
