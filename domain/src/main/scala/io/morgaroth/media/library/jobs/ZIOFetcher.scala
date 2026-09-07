package io.morgaroth.media.library.jobs

import com.typesafe.scalalogging.LazyLogging
import io.morgaroth.media.library.common.given_Ordering_ZonedDateTime
import io.morgaroth.media.library.storage.{Track, ZioTracksStorageService}
import io.morgaroth.media.library.{Args, Configuration}
import zio.*

import java.io.File
import java.nio.file.Files
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

  def handleUrl(cfg: Configuration)(using definition: Track): Task[Either[Throwable, Unit]] =
    val versionId = definition.UFID + algorithmVersion
    val destination = File(cfg.destinationDir, s"${definition.title} - ${definition.artist}.mp3")
    if destination.exists() && cfg.forceLevel < 1 && getUFIDTag(destination).contains(versionId) then
      logger.info("File {} already downloaded", definition.info)
      ZIO.succeed(Right(()))
    else
      val work = for
        _ <- ZIO.attempt(active.incrementAndGet())
        sem <- Semaphore.make(4)
        a <- sem.withPermit(
          ZIO.logInfo(s"Downloading ${definition.info}...") *>
            download(definition.url, cfg.cacheLocation, cfg.downloaderExec, cfg.debug) <*
            ZIO.logInfo(s"Downloaded ${definition.info}, now converting...")
        )
        (source, ytMeta) = a
        mp3file = convertToMP3(source, cfg, definition)
        trimmed = strip(mp3file, definition, cfg.debug)
        finalFile = dist(trimmed, destination)
        _ <- addTags(
          album = Option(definition.album).filter(_.nonEmpty).getOrElse("Twórczość"),
          author = definition.artist,
          title = definition.title,
          file = finalFile,
          id = versionId,
        )
        _ <- setTimestamps(finalFile, definition.createdAtLocal, definition.updatedAtLocal)
        _ <- storage.updateRawTitle(definition._id, ytMeta.title).when(definition.rawTitle != ytMeta.title)
        _ <- storage.updateRawDescription(definition._id, ytMeta.description).when(definition.rawDescription != ytMeta.description)
        _ <- ZIO.fromEither(copyFileToPlaylistDirectories(cfg.destinationDir, destination, definition))
        _ <- ZIO.logInfo(s"File ${definition.info} ready")
      yield Right(())

      work.catchSome:
        case t: URLFetchError =>
          logger.warn(s"The track needs to be updated ${t.getMessage}")
          ZIO.succeed(Left(t))
        case t: Throwable =>
          logger.error(s"Error $t during handling ${definition.url}, going forward...")
          t.printStackTrace()
          ZIO.succeed(Left(t))
      .ensuring(ZIO.succeed(active.decrementAndGet()))

  private def doAllWork(definitions: Vector[Track], cfg: Configuration): Task[Unit] =
    ZIO.attempt(cfg.destinationDir.mkdirs()) *>
      ZIO.foreachParDiscard(definitions) { defi =>
        given Track = defi
        handleUrl(cfg) *> ZIO.attempt:
          val thisId = id.incrementAndGet()
          logger.info(s"$thisId/${definitions.size} ready, ${active.get()} active, ${activeDownloads.get()} active downloads")
      }.withParallelism(10)

  def copyFileToPlaylistDirectories(destinationDir: File, file: File, track: Track): Either[Throwable, Unit] =
    track.playlists.foldLeft(Right(()): Either[Throwable, Unit]):
      case (acc, playlist) =>
        acc.flatMap: _ =>
          try
            logger.info(s"Copying ${file.getName} to $playlist")
            val playlistDir = File(destinationDir, playlist)
            if !playlistDir.exists() then
              logger.info(s"$playlistDir does not exist, creating...")
              playlistDir.mkdir()
            Files.copy(file.toPath, File(playlistDir, file.getName).toPath)
            Right(())
          catch case e: Throwable => Left(e)

  def setTimestamps(file: File, created: LocalDateTime, modified: LocalDateTime): Task[Unit] =
    ZIO.attempt:
      Files.setAttribute(file.toPath, "creationTime", FileTime.fromMillis(created.toInstant(ZoneOffset.UTC).toEpochMilli))
      file.setLastModified(modified.toInstant(ZoneOffset.UTC).toEpochMilli)

  def download(url: String, dest: File, downloaderExec: String, debug: Boolean = false)(using track: Track): Task[(File, YoutubeDLMeta)] =

    def doWork(retries: Int = 5): Either[Throwable, (File, YoutubeDLMeta)] =
      val work = for
        ytMetadata <- MetaDataFetcher.getYTMetadata(downloaderExec, url, Some("%(title)s (%(id)s) - RAW.%(ext)s"))
        destinationPath = File(dest, ytMetadata._filename.get)
        _ <-
          if destinationPath.exists() then
            logger.info(s"Using previously downloaded file $destinationPath.")
            Right(())
          else
            activeDownloads.incrementAndGet()
            val args = Seq(downloaderExec, "--restrict-filenames", "--cookies-from-browser", "chrome", "-f", "bestaudio", "-o", destinationPath.toPath.toString, url)
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
    destination.delete()
    assert(source.renameTo(destination), "cannot move")
    destination

  def addTags(file: File, author: String, title: String, album: String, id: String): Task[String] =
    ZIO.attempt:
      Seq("mid3v2", file.getAbsolutePath, "-t", title, "-a", author, "-A", album, "--UFID", s"definitionhash:$id").!!

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
