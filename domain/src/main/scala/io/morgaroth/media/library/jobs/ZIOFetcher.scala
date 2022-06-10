package io.morgaroth.media.library.jobs

import cats.implicits.catsSyntaxEitherId
import cats.syntax.either._
import cats.syntax.option._
import com.typesafe.scalalogging.LazyLogging
import io.morgaroth.media.library.common._
import io.morgaroth.media.library.storage.{Track, ZioTracksStorageService}
import io.morgaroth.media.library.{Args, Configuration}
import zio.console.putStrLn
import zio._

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.time.{LocalDateTime, ZoneOffset}
import java.util.concurrent.atomic.AtomicInteger
import scala.sys.process._

class ZIOFetcher(storage: ZioTracksStorageService) extends LazyLogging {

  val algorithmVersion = "4"

  def main(args: List[String]) = {
    for {
      cfg <- Task.fromEither(Args(args).toRight(new IllegalArgumentException("can't load config")))
      definitions <- storage.findAllReadyToFetch.map(_.sortBy(_.updatedAt).reverse)
      filtered = definitions.filter(_.info.matches(cfg.regex))
      _ <- doAllWork(filtered, cfg)
    } yield ()
  }

  val active = new AtomicInteger()
  val activeDownloads = new AtomicInteger()
  val inc = zio.Semaphore.make(permits = 1)
  val downloadSem = zio.Semaphore.make(permits = 4)

  def handleUrl(cfg: Configuration)(implicit definition: Track) = {
    val versionId = definition.UFID + algorithmVersion
    val destination = new File(cfg.destinationDir, s"${definition.title} - ${definition.artist}.mp3")
    if (destination.exists() && cfg.forceLevel < 1 && getUFIDTag(destination).contains(versionId)) {
      logger.info("File {} already downloaded", definition.info)
      Task.unit.map(_.asRight)
    } else {
      val work = for {
        _ <- Task.effect(active.incrementAndGet())
        a <- downloadSem.flatMap(_.withPermit(
          (putStrLn(s"Downloading ${definition.info}...") *> download(definition.url, cfg.cacheLocation, cfg.downloaderExec, cfg.debug)) <* putStrLn(s"Downloaded ${definition.info}, now converting...")
        ))
        (source, ytMeta) = a
        mp3file = convertToMP3(source, cfg, definition)
        trimmed = strip(mp3file, definition, cfg.debug)
        finalFile = dist(trimmed, destination)
        _ <- addTags(
          album = definition.album.some.filter(_.nonEmpty).getOrElse("Twórczość"),
          author = definition.artist,
          title = definition.title,
          file = finalFile,
          id = versionId,
        )
        _ <- setTimestamps(finalFile, definition.createdAtLocal, definition.updatedAtLocal)
        _ <- storage.updateRawTitle(definition._id, ytMeta.title).unless(definition.rawTitle == ytMeta.title)
        _ <- storage.updateRawDescription(definition._id, ytMeta.title).unless(definition.rawDescription == ytMeta.description)
        _ <- Task.fromEither(copyFileToPlaylistDirectories(cfg.destinationDir, destination, definition))
        _ <- putStrLn(s"File ${definition.info} ready")
      } yield ().asRight

      work.catchSome {
        case t: URLFetchError =>
          logger.warn(s"The track needs to be updated ${t.getMessage}")
          Task.unit.map(_.asLeft)
        case t: Throwable =>
          logger.error(s"Error $t during handling ${definition.url}, going forward... [1]")
          t.printStackTrace()
          Task.unit.map(_.asLeft)
      } <*
        ZIO.effect(active.decrementAndGet())
    }
  }

  val id = new AtomicInteger()

  private def doAllWork(definitions: Vector[Track], cfg: Configuration) = {
    Task.effect(cfg.destinationDir.mkdirs()) *>
      ZIO
        .foreachParN_(10)(definitions) { defi =>
          handleUrl(cfg)(defi) <* inc.flatMap(_.withPermit(Task.effect {
            val thisId = id.incrementAndGet()
            logger.info(s"$thisId/${definitions.size} ready, ${active.get()} active, ${activeDownloads.get()} active downloads")
          }))
        }
  }

  def copyFileToPlaylistDirectories(destinationDir: File, file: File, track: Track): Either[Throwable, Unit] = {
    track.playlists.foldLeft(().asRight[Throwable]) {
      case (acc, playlist) =>
        acc.flatMap { _ =>
          Either.catchNonFatal {
            logger.info(s"Copying ${file.getName} to $playlist")
            val playlistDir = new File(destinationDir, playlist)
            if (!playlistDir.exists()) {
              logger.info(s"$playlistDir does not exist, creating...")
              playlistDir.mkdir()
            }
            Files.copy(file.toPath, new File(playlistDir, file.getName).toPath)
          }
        }
    }
  }

  def setTimestamps(file: File, created: LocalDateTime, modified: LocalDateTime): Task[Unit] = {
    Task.effect {
      Files.setAttribute(file.toPath, "creationTime", FileTime.fromMillis(created.toInstant(ZoneOffset.UTC).toEpochMilli))
      file.setLastModified(modified.toInstant(ZoneOffset.UTC).toEpochMilli)
    }
  }

  def download(url: String, dest: File, downloaderExec: String, debug: Boolean = false)(implicit track: Track): Task[(File, YoutubeDLMeta)] = {

    def doWork(retries: Int = 5): Either[Throwable, (File, YoutubeDLMeta)] = {
      val work = for {
        ytMetadata <- MetaDataFetcher.getYTMetadata(downloaderExec, url, Some("%(title)s (%(id)s) - RAW.%(ext)s"))
        destinationPath = new File(dest, ytMetadata._filename.get)
        _ <- if (destinationPath.exists()) {
          logger.info(s"Using previously downloaded file $destinationPath.")
          ().asRight
        } else {
          activeDownloads.incrementAndGet()
          val args = Seq(downloaderExec, /*"--print-json",*/ "--restrict-filenames", "-f", "mp4", "-o", destinationPath.toPath.toString, url)
          if (debug) {
            logger.debug("--> {}", args.mkString(" "))
          }
          val result = Either.catchNonFatal(args.!!<)
          activeDownloads.decrementAndGet()
          result
        }
      } yield (destinationPath, ytMetadata)

      work.recoverWith {
        case _: Throwable if retries > 0 =>
          logger.warn(s"error during downloading link $url")
          doWork(retries - 1)
        case t: Throwable =>
          logger.error(s"Converting ${t.getMessage} to URLFetchError")
          Left(new URLFetchError(track.title, track.artist, track.album, track.searchUrl))
      }
    }

    Task.fromEither(doWork())
  }

  private def ffmpeg(inputArgs: String*)(input: File)(outputArgs: String*)(output: File)(quiet: Boolean, debug: Boolean) = {
    val logging = if (quiet) Seq("-loglevel", "panic") else Seq.empty
    val sbStdOut = StringBuilder.newBuilder
    val sbStdErr = StringBuilder.newBuilder
    val l = ProcessLogger(sbStdOut.append(_).append("\n"), sbStdErr.append(_).append("\n"))
    val args: Seq[String] = Vector(
      Seq("ffmpeg"),
      logging,
      inputArgs,
      Seq("-i", input.getAbsolutePath),
      outputArgs :+ output.getAbsolutePath
    ).flatten

    if (debug) {
      logger.debug("--> {}", args.mkString(" "))
    }
    val stdOut = args.!!(l)
    if (stdOut.isEmpty && sbStdOut.isEmpty && sbStdErr.nonEmpty) {
      sbStdErr.mkString
    } else if (stdOut.isEmpty && sbStdOut.nonEmpty && sbStdErr.isEmpty) {
      sbStdOut.mkString
    } else if (stdOut.nonEmpty && sbStdOut.isEmpty && sbStdErr.isEmpty) {
      stdOut
    } else if (stdOut.isEmpty && sbStdOut.isEmpty && sbStdErr.isEmpty) {
      ""
    } else {
      throw new IllegalArgumentException(s"multiple sources contain data! raw: ${stdOut.length}, sbOut: ${sbStdOut.length} sbErr: ${sbStdErr.length}")
    }
  }

  private def findMaxValue(file: File, debug: Boolean) = {
    val output = ffmpeg()(file)("-af", "volumedetect", "-sn", "-dn", "-f", "null")(new File("/dev/null"))(quiet = false, debug)
    val maxVolumeLine = output.split("\n").filter(_.contains("max_volume:"))
    assert(maxVolumeLine.length == 1)
    val value = maxVolumeLine.head.split(" ")(4)
    value.toDouble
  }

  private def convertToMP3(source: File, config: Configuration, track: Track) = {
    val target = new File(source.getParent, source.getName.split("""\.""").init.mkString + ".mp3")
    if (!target.exists() || config.forceLevel > 1) {
      val maxVolume = Option(Option(findMaxValue(source, config.debug)).map(_ * -1).getOrElse(0d) + track.volumeChange.map(_.toDouble).getOrElse(0d)).filter(_ != 0)
      target.delete()
      val reVolumeArgs = maxVolume.map(x => Seq("-af", s"volume=${x}dB")).getOrElse(Seq.empty)
      ffmpeg()(source)(reVolumeArgs ++ Seq("-q:a", "0", "-acodec", "libmp3lame"): _*)(target)(quiet = true, debug = config.debug)
    }
    target
  }

  private def strip(source: File, definition: Track, debug: Boolean) = {
    val t1 = new File(source.getParentFile, "TMP - " + source.getName)
    t1.delete()
    t1.deleteOnExit()
    val (a, b) = fixDuration(definition.startAt, definition.endAt)
    val codecsInfo = definition.fadeOutSeconds.map { secs =>
      val diff1 = b.get.split(":").map(_.toInt)
      val targetTrackSeconds = diff1.head * 3600 + diff1(1) * 60 + diff1(2)
      Seq("-filter_complex", s"afade=t=out:st=${targetTrackSeconds - secs}:d=$secs", "-q:a", "0", "-acodec", "libmp3lame")
    }.getOrElse(Seq("-acodec", "copy"))
    ffmpeg(a.toList.flatMap("-ss" :: _ :: Nil): _*)(source)(
      codecsInfo ++ b.toList.flatMap("-to" :: _ :: Nil): _*,
    )(t1)(quiet = true, debug = debug)
    t1
  }

  private def dist(source: File, destination: File) = {
    destination.delete()
    assert(source.renameTo(destination), "cannot move")
    destination
  }

  def addTags(file: File, author: String, title: String, album: String, id: String): Task[String] = {
    Task.effect {
      Seq("mid3v2", file.getAbsolutePath, "-t", title, "-a", author, "-A", album, "--UFID", s"definitionhash:$id").!!
    }
  }

  def getUFIDTag(file: File): Option[String] = {
    Seq("mid3v2", file.getAbsolutePath, "--list").!!.split("\n").find(_.startsWith("UFID=definitionhash=")).map(_.stripPrefix("UFID=definitionhash=").filterNot(_ == '\'').trim)
  }

  private def normalizeIt(input: String) = {
    val result = input.split(":").toList match {
      case Nil => throw new IllegalArgumentException("invalid data in input")
      case singleSeconds :: Nil => "00:00:" + s"0$singleSeconds".takeRight(2)
      case minutes :: seconds :: Nil => s"00:${
        s"0$minutes".takeRight(2)
      }:$seconds"
    }
    assert(result.length == 8)
    result
  }

  def fixDuration(start: Option[String], end: Option[String]): (Option[String], Option[String]) = {
    start.zip(end).map {
      case (x, y) =>
        val s = x.split(":").map(_.toInt)
        val e = y.split(":").map(_.toInt)
        val diff = (e.head - s.head) * 60 + e(1) - s(1)
        val diffM = ("0" + diff / 60).takeRight(2)
        val diffS = ("0" + diff % 60).takeRight(2)
        (start.map(normalizeIt), Some(s"00:$diffM:$diffS"))
    }.getOrElse((start.map(normalizeIt), end.map(normalizeIt)))
  }
}

object ZIOFetcher {
  val live: URLayer[Has[ZioTracksStorageService], Has[ZIOFetcher]] = ZLayer.fromService(new ZIOFetcher(_: ZioTracksStorageService))
}
