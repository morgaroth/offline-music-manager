package io.morgaroth.media.library.jobs

import com.typesafe.scalalogging.LazyLogging
import io.morgaroth.media.library.common.given_Ordering_ZonedDateTime
import io.morgaroth.media.library.storage.{MediaType, Track, ZioTracksStorageService}
import io.morgaroth.media.library.{Args, Configuration}
import zio.*

import java.io.File
import java.nio.file.{Files, StandardCopyOption}
import java.nio.file.attribute.FileTime
import java.time.{LocalDateTime, ZoneOffset}
import java.util.concurrent.atomic.AtomicInteger
import scala.sys.process.*

class ZIOFetcher(storage: ZioTracksStorageService) extends LazyLogging:

  val algorithmVersion = "4"

  def main(args: List[String]): Task[Unit] =
    for
      cfg <- ZIO.fromEither(Args(args).toRight(IllegalArgumentException("can't load config")))
      definitions <- storage.findAllReadyToFetch.map(_.sortBy(_.updatedAt).reverse)
      filtered = definitions.filter(_.info.matches(cfg.regex))
      _ <- doAllWork(filtered, cfg)
    yield ()

  private val active = AtomicInteger()
  private val activeDownloads = AtomicInteger()
  private val id = AtomicInteger()

  /** Dispatch on media type: music -> mp3 extraction; video -> H.264/AAC mp4. */
  def handleUrl(cfg: Configuration)(using definition: Track): Task[Either[Throwable, Unit]] =
    definition.mediaType match
      case MediaType.Video => handleVideo(cfg)
      case MediaType.Music => handleMusic(cfg)

  private def handleMusic(cfg: Configuration)(using definition: Track): Task[Either[Throwable, Unit]] =
    val versionId = definition.UFID + algorithmVersion
    val fileName = s"${definition.title} - ${definition.artist}.mp3"
    // A track lives only under its playlist folders — no generic root copy.
    // Every track has >= 1 playlist, so there is always at least one target.
    val playlists = definition.playlists.filter(_.nonEmpty)
    val targets = playlists.map(p => File(File(cfg.destinationDir, p), fileName)).toVector
    // Skip only if ALL targets already exist with the current version.
    val alreadyDone =
      targets.nonEmpty && cfg.forceLevel < 1 &&
        targets.forall(f => f.exists() && getUFIDTag(f).contains(versionId))
    if alreadyDone then
      logger.info("File {} already present in all playlists", definition.info)
      ZIO.succeed(Right(()))
    else
      val work = for
        _ <- ZIO.attempt(active.incrementAndGet())
        sem <- Semaphore.make(4)
        workDir = workDirFor(cfg, definition)
        a <- sem.withPermit(
          ZIO.logInfo(s"Downloading ${definition.info}...") *>
            download(definition.url, workDir, cfg.downloaderExec, cfg.cookieArgs, cfg.debug) <*
            ZIO.logInfo(s"Downloaded ${definition.info}, now converting...")
        )
        (source, ytMeta) = a
        _ <- ZIO.logInfo(s"[pipeline] downloaded source: ${source.getAbsolutePath} (exists=${source.exists()}); playlists: ${playlists.mkString(", ")}")
        mp3file = convertToMP3(source, cfg, definition)
        _ <- ZIO.logInfo(s"[pipeline] converted mp3: ${mp3file.getAbsolutePath} (exists=${mp3file.exists()})")
        trimmed = strip(mp3file, definition, cfg.debug)
        _ <- ZIO.logInfo(s"[pipeline] trimmed: ${trimmed.getAbsolutePath} (exists=${trimmed.exists()})")
        // Tag the single built file in the work dir, then copy into each playlist.
        taggedName = sanitizeFileName(fileName)
        tagged = File(workDir, taggedName)
        _ = dist(trimmed, tagged)
        _ <- addTags(
          album = Option(definition.album).filter(_.nonEmpty).getOrElse("Twórczość"),
          author = definition.artist,
          title = definition.title,
          file = tagged,
          id = versionId,
        )
        _ <- setTimestamps(tagged, definition.createdAtLocal, definition.updatedAtLocal)
        _ <- storage.updateRawTitle(definition._id, ytMeta.title).when(definition.rawTitle != ytMeta.title)
        _ <- storage.updateRawDescription(definition._id, ytMeta.description).when(definition.rawDescription != ytMeta.description)
        _ <- ZIO.foreachDiscard(targets)(t => ZIO.attempt(copyInto(tagged, t)))
        _ <- ZIO.attempt(deleteRecursively(workDir)).ignore
        _ <- ZIO.logInfo(s"File ${definition.info} ready in ${targets.size} playlist(s)")
      yield Right(())

      work.catchSome:
        case t: URLFetchError =>
          logger.warn(s"The track needs to be updated: ${t.getMessage}")
          ZIO.succeed(Left(t))
        case t: Throwable =>
          // Log the full cause with stack trace so pipeline failures (e.g. a
          // cross-device move) are diagnosable instead of a bare message.
          logger.error(s"Error handling ${definition.info} (${definition.url}): ${t.getClass.getName}: ${t.getMessage}", t)
          ZIO.succeed(Left(t))
      .ensuring(ZIO.succeed(active.decrementAndGet()))

  // ---- Video pipeline -------------------------------------------------------

  /** Fetch a video and transcode to H.264 (High) + AAC in MP4 — the broadly
    * compatible target that both Jellyfin and older webOS TVs can play. Grabs
    * the best available source quality, then re-encodes. Output goes to the
    * separate video destination dir (Jellyfin's mount).
    */
  private def handleVideo(cfg: Configuration)(using definition: Track): Task[Either[Throwable, Unit]] =
    val safeName = sanitizeFileName(s"${definition.title} - ${definition.artist}".trim.stripSuffix("-").trim)
    val fileName = s"$safeName.mkv"
    // Same as music: one copy per playlist under the video dir, no root copy.
    val playlists = definition.playlists.filter(_.nonEmpty)
    val targets = playlists.map(p => File(File(cfg.videoDestinationDir, p), fileName)).toVector
    val alreadyDone = targets.nonEmpty && cfg.forceLevel < 1 && targets.forall(_.exists())
    if alreadyDone then
      logger.info("Video {} already present in all playlists", definition.info)
      ZIO.succeed(Right(()))
    else
      val work = for
        _ <- ZIO.attempt(active.incrementAndGet())
        workDir = workDirFor(cfg, definition)
        source <- ZIO.logInfo(s"Downloading video ${definition.info}...") *>
          downloadVideo(definition.url, workDir, cfg.downloaderExec, cfg.cookieArgs, cfg.debug)
        _ <- ZIO.logInfo(s"[pipeline] downloaded video source: ${source.getAbsolutePath} (exists=${source.exists()}); playlists: ${playlists.mkString(", ")}")
        // No re-encode. If start/end trim is set, do a fast stream-copy cut;
        // otherwise use the downloaded file as-is.
        finalFile <- ZIO.attemptBlocking(trimVideoIfNeeded(source, cfg, definition))
        _ <- ZIO.logInfo(s"[pipeline] video ready to distribute: ${finalFile.getAbsolutePath} (exists=${finalFile.exists()})")
        _ <- setTimestamps(finalFile, definition.createdAtLocal, definition.updatedAtLocal)
        _ <- ZIO.foreachDiscard(targets)(t => ZIO.attempt(copyInto(finalFile, t)))
        _ <- ZIO.attempt(deleteRecursively(workDir)).ignore
        _ <- ZIO.logInfo(s"Video ${definition.info} ready in ${targets.size} playlist(s)")
      yield Right(())

      work.catchSome:
        case t: URLFetchError =>
          logger.warn(s"The video needs to be updated: ${t.getMessage}")
          ZIO.succeed(Left(t))
        case t: Throwable =>
          logger.error(s"Error handling video ${definition.info} (${definition.url}): ${t.getClass.getName}: ${t.getMessage}", t)
          ZIO.succeed(Left(t))
      .ensuring(ZIO.succeed(active.decrementAndGet()))

  /** Download best video+audio up to 1080p, merged into an MKV container with
    * NO re-encode. We keep whatever codec YouTube serves (VP9/AV1/H.264) and let
    * Jellyfin handle any transcoding at playback. MKV holds any codec cleanly.
    */
  private def downloadVideo(url: String, dest: File, downloaderExec: String, cookieArgs: Seq[String], debug: Boolean)(using track: Track): Task[File] =
    ZIO.fromEither:
      def doWork(retries: Int = 3): Either[Throwable, File] =
        val outTemplate = File(dest, "%(title)s (%(id)s) - VIDEO.%(ext)s").toPath.toString
        val args = Seq(downloaderExec) ++ cookieArgs ++ Seq(
          "--restrict-filenames",
          // Cap at 1080p (no 4K/UHD) to save space; take whatever codec is best
          // at that resolution — no local transcode.
          "-f", "bestvideo[height<=1080]+bestaudio/best[height<=1080]/best",
          // Remux (not re-encode) into mkv, which accepts VP9/AV1/Opus/AAC/H.264.
          "--merge-output-format", "mkv",
          "--print", "after_move:filepath",
          "--no-simulate",
          "-o", outTemplate,
          url,
        )
        if debug then logger.debug("--> {}", args.mkString(" "))
        activeDownloads.incrementAndGet()
        val result =
          try
            val printed = args.!!.trim.linesIterator.toList.lastOption.getOrElse("")
            val f = File(printed)
            if printed.nonEmpty && f.exists() then Right(f)
            else Left(RuntimeException(s"yt-dlp did not report an output file (got '$printed')"))
          catch case e: Throwable => Left(e)
        activeDownloads.decrementAndGet()
        result match
          case Left(_) if retries > 0 =>
            logger.warn(s"error downloading video $url, retrying")
            doWork(retries - 1)
          case Left(t) =>
            Left(URLFetchError(track.title, track.artist, track.album, track.searchUrl))
          case right => right
      doWork()

  /** Apply optional start/end trim with a stream COPY (no re-encode) when set;
    * otherwise return the source untouched. Keeps the original codec.
    */
  private def trimVideoIfNeeded(source: File, config: Configuration, track: Track): File =
    val (startOpt, endOpt) = fixDuration(track.startAt, track.endAt)
    if startOpt.isEmpty && endOpt.isEmpty then source
    else
      val target = File(source.getParentFile, sanitizeFileName(s"${track.title} - ${track.artist}") + " - CUT.mkv")
      target.delete()
      val trimIn = startOpt.toList.flatMap(v => List("-ss", v))
      val trimOut = endOpt.toList.flatMap(v => List("-to", v))
      // Stream copy: fast, lossless, keeps whatever codec YouTube served.
      ffmpeg(trimIn*)(source)((Seq("-c", "copy") ++ trimOut)*)(target)(quiet = true, debug = config.debug)
      target

  private def sanitizeFileName(s: String): String =
    s.replaceAll("""[/\\:*?"<>|]""", "_").trim

  /** Copy `src` to `dest`, creating the parent dir, overwriting if present.
    * Cross-filesystem safe (plain copy, not rename).
    */
  private def copyInto(src: File, dest: File): Unit =
    Option(dest.getParentFile).foreach(_.mkdirs())
    Files.copy(src.toPath, dest.toPath, StandardCopyOption.REPLACE_EXISTING)
    logger.info(s"[dist] copied -> ${dest.getAbsolutePath} (${dest.length} bytes)")

  /** A per-track working directory under the cache root. Isolates concurrent
    * fetches so two tracks sharing a source URL can't collide on temp filenames.
    */
  private def workDirFor(cfg: Configuration, track: Track): File =
    val d = File(cfg.cacheLocation, track._id.toString)
    d.mkdirs()
    d

  private def deleteRecursively(f: File): Unit =
    if f.isDirectory then Option(f.listFiles).foreach(_.foreach(deleteRecursively))
    f.delete()

  private def doAllWork(definitions: Vector[Track], cfg: Configuration): Task[Unit] =
    ZIO.attempt(cfg.destinationDir.mkdirs()) *>
      ZIO.foreachParDiscard(definitions) { defi =>
        given Track = defi
        handleUrl(cfg) *> ZIO.attempt:
          val thisId = id.incrementAndGet()
          logger.info(s"$thisId/${definitions.size} ready, ${active.get()} active, ${activeDownloads.get()} active downloads")
      }.withParallelism(10)

  def setTimestamps(file: File, created: LocalDateTime, modified: LocalDateTime): Task[Unit] =
    ZIO.attempt:
      Files.setAttribute(file.toPath, "creationTime", FileTime.fromMillis(created.toInstant(ZoneOffset.UTC).toEpochMilli))
      file.setLastModified(modified.toInstant(ZoneOffset.UTC).toEpochMilli)

  def download(url: String, dest: File, downloaderExec: String, cookieArgs: Seq[String] = Seq.empty, debug: Boolean = false)(using track: Track): Task[(File, YoutubeDLMeta)] =

    def doWork(retries: Int = 5): Either[Throwable, (File, YoutubeDLMeta)] =
      val work = for
        ytMetadata <- MetaDataFetcher.getYTMetadata(downloaderExec, url, Some("%(title)s (%(id)s) - RAW.%(ext)s"), cookieArgs)
        destinationPath = File(dest, ytMetadata._filename.get)
        _ <-
          if destinationPath.exists() then
            logger.info(s"Using previously downloaded file $destinationPath.")
            Right(())
          else
            activeDownloads.incrementAndGet()
            val args = Seq(downloaderExec, "--restrict-filenames") ++ cookieArgs ++ Seq("-f", "bestaudio", "-o", destinationPath.toPath.toString, url)
            if debug then logger.debug("--> {}", args.mkString(" "))
            val result = try Right(args.!!) catch case e: Throwable => Left(e)
            activeDownloads.decrementAndGet()
            result
      yield (destinationPath, ytMetadata)

      work match
        case Left(_) if retries > 0 =>
          logger.warn(s"error during downloading link $url")
          doWork(retries - 1)
        case Left(t) =>
          logger.error(s"Converting ${t.getMessage} to URLFetchError")
          Left(URLFetchError(track.title, track.artist, track.album, track.searchUrl))
        case right => right

    ZIO.fromEither(doWork())

  private def ffmpeg(inputArgs: String*)(input: File)(outputArgs: String*)(output: File)(quiet: Boolean, debug: Boolean): String =
    val logging = if quiet then Seq("-loglevel", "panic") else Seq.empty
    val sbStdOut = StringBuilder()
    val sbStdErr = StringBuilder()
    val l = ProcessLogger(s => sbStdOut.append(s).append("\n"), s => sbStdErr.append(s).append("\n"))
    val args: Seq[String] = Vector(
      Seq("ffmpeg", "-y"),
      logging,
      inputArgs,
      Seq("-i", input.getAbsolutePath),
      outputArgs :+ output.getAbsolutePath
    ).flatten

    if debug then logger.debug("--> {}", args.mkString(" "))
    val exitCode = args.!(l)
    if exitCode != 0 then
      logger.warn(s"ffmpeg exited with code $exitCode: ${sbStdErr.mkString.take(200)}")
    sbStdErr.mkString + sbStdOut.mkString

  private def findMaxValue(file: File, debug: Boolean): Double =
    println(s"[ffmpeg] volumedetect on ${file.getName}")
    val output = ffmpeg()(file)("-af", "volumedetect", "-sn", "-dn", "-f", "null")(File("/dev/null"))(quiet = false, debug)
    println(s"[ffmpeg] volumedetect done, output length=${output.length}")
    val maxVolumeLine = output.split("\n").filter(_.contains("max_volume:"))
    if maxVolumeLine.isEmpty then
      println(s"[ffmpeg] WARNING: no max_volume found in output, defaulting to 0.0")
      0.0
    else
      maxVolumeLine.head.split(" ")(4).toDouble

  private def convertToMP3(source: File, config: Configuration, track: Track): File =
    val target = File(source.getParent, source.getName.split("""\.""").init.mkString + ".mp3")
    println(s"[convert] source=${source.getName}, target=${target.getName}, exists=${target.exists()}")
    if !target.exists() || config.forceLevel > 1 then
      println(s"[convert] running volume detection...")
      val maxVolume = Option(Option(findMaxValue(source, config.debug)).map(_ * -1).getOrElse(0d) + track.volumeChange.map(_.toDouble).getOrElse(0d)).filter(_ != 0)
      target.delete()
      val reVolumeArgs = maxVolume.map(x => Seq("-af", s"volume=${x}dB")).getOrElse(Seq.empty)
      println(s"[convert] encoding to mp3...")
      ffmpeg()(source)(reVolumeArgs ++ Seq("-q:a", "0", "-acodec", "libmp3lame")*)(target)(quiet = true, debug = config.debug)
      println(s"[convert] done, target exists=${target.exists()}")
    else
      println(s"[convert] skipped, target already exists")
    target

  private def strip(source: File, definition: Track, debug: Boolean): File =
    val t1 = File(source.getParentFile, "TMP - " + source.getName)
    t1.delete()
    t1.deleteOnExit()
    val (a, b) = fixDuration(definition.startAt, definition.endAt)
    val codecsInfo = definition.fadeOutSeconds.map: secs =>
      val diff1 = b.get.split(":").map(_.toInt)
      val targetTrackSeconds = diff1.head * 3600 + diff1(1) * 60 + diff1(2)
      Seq("-filter_complex", s"afade=t=out:st=${targetTrackSeconds - secs}:d=$secs", "-q:a", "0", "-acodec", "libmp3lame")
    .getOrElse(Seq("-acodec", "copy"))
    ffmpeg(a.toList.flatMap(v => List("-ss", v))*)(source)(
      (codecsInfo ++ b.toList.flatMap(v => List("-to", v)))*
    )(t1)(quiet = true, debug = debug)
    t1

  private def dist(source: File, destination: File): File =
    // The source lives in the cache dir; the destination is often on a different
    // filesystem (e.g. an NFS mount). File.renameTo only works within one
    // filesystem, so use Files.move and fall back to copy+delete across devices.
    logger.info(s"[dist] moving ${source.getAbsolutePath} (${source.length} bytes, exists=${source.exists()}) -> ${destination.getAbsolutePath}")
    Option(destination.getParentFile).foreach(_.mkdirs())
    try
      Files.move(source.toPath, destination.toPath, StandardCopyOption.REPLACE_EXISTING)
    catch
      case _: java.nio.file.AtomicMoveNotSupportedException | _: java.nio.file.FileSystemException =>
        logger.info(s"[dist] cross-device move; copying then deleting source")
        Files.copy(source.toPath, destination.toPath, StandardCopyOption.REPLACE_EXISTING)
        source.delete()
    if !destination.exists() then
      throw RuntimeException(s"[dist] destination missing after move: ${destination.getAbsolutePath}")
    logger.info(s"[dist] done: ${destination.getAbsolutePath} (${destination.length} bytes)")
    destination

  def addTags(file: File, author: String, title: String, album: String, id: String): Task[String] =
    // Write well-formed ID3v2.3 tags via mutagen. mid3v2 writes v2.4 by default,
    // which some scanners (incl. older Navidrome/TagLib setups) mis-read, showing
    // [Unknown Artist]/[Unknown Album]. v2.3 is the broadly-compatible version.
    // We also set TPE2 (album artist) so Navidrome groups albums correctly.
    // Values are passed via argv (not string-interpolated) to avoid quoting bugs.
    val script =
      """|import sys
         |from mutagen.id3 import ID3, ID3NoHeaderError, TIT2, TPE1, TPE2, TALB, UFID
         |path, title, artist, album, ufid = sys.argv[1:6]
         |try:
         |    tags = ID3(path)
         |except ID3NoHeaderError:
         |    tags = ID3()
         |tags.setall('TIT2', [TIT2(encoding=3, text=[title])])
         |tags.setall('TPE1', [TPE1(encoding=3, text=[artist])])
         |tags.setall('TPE2', [TPE2(encoding=3, text=[artist])])
         |tags.setall('TALB', [TALB(encoding=3, text=[album])])
         |tags.setall('UFID', [UFID(owner='definitionhash', data=ufid.encode())])
         |tags.update_to_v23()
         |tags.save(path, v2_version=3)
         |""".stripMargin
    ZIO.attempt:
      Seq("python3", "-c", script, file.getAbsolutePath, title, author, album, id).!!

  def getUFIDTag(file: File): Option[String] =
    Seq("mid3v2", file.getAbsolutePath, "--list").!!
      .split("\n")
      .find(_.startsWith("UFID=definitionhash="))
      .map(_.stripPrefix("UFID=definitionhash=").filterNot(_ == '\'').trim)

  private def normalizeIt(input: String): String =
    val result = input.split(":").toList match
      case Nil => throw IllegalArgumentException("invalid data in input")
      case singleSeconds :: Nil => "00:00:" + s"0$singleSeconds".takeRight(2)
      case minutes :: seconds :: Nil => s"00:${s"0$minutes".takeRight(2)}:$seconds"
      case _ => input
    assert(result.length == 8)
    result

  def fixDuration(start: Option[String], end: Option[String]): (Option[String], Option[String]) =
    (start, end) match
      case (Some(x), Some(y)) =>
        val s = x.split(":").map(_.toInt)
        val e = y.split(":").map(_.toInt)
        val diff = (e.head - s.head) * 60 + e(1) - s(1)
        val diffM = ("0" + diff / 60).takeRight(2)
        val diffS = ("0" + diff % 60).takeRight(2)
        (start.map(normalizeIt), Some(s"00:$diffM:$diffS"))
      case _ => (start.map(normalizeIt), end.map(normalizeIt))

object ZIOFetcher:
  val live: ZLayer[ZioTracksStorageService, Nothing, ZIOFetcher] =
    ZLayer.fromFunction(ZIOFetcher(_))
