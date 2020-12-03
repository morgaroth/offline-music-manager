package io.morgaroth.media.library.jobs

import cats.syntax.either._
import cats.syntax.option._
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import io.circe.DecodingFailure
import io.circe.generic.auto._
import io.circe.parser._
import io.morgaroth.media.library.storage.{Track, TracksStorage}
import io.morgaroth.media.library.{Args, Configuration}
import org.joda.time.LocalDateTime

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{Await, Future}
import scala.sys.process._

object MetaDataFetcher {

  def getMetadata(url: String) = {
    val cfg = Configuration()
    val result = getYTMetadata(cfg.downloaderExec, url, None)
    println(result)
    result
  }

  def getYTMetadata(downloaderExec: String, url: String, format: Option[String]): Either[Throwable, YoutubeDLMeta] = {
    Either.catchNonFatal {
      (Seq(downloaderExec, "--print-json", "-s", url) ++ format.toSeq.flatMap(x => Seq("-o", x))).!!<
    }.flatMap { json =>
      decode[YoutubeDLMeta](json).left.map {
        case c: DecodingFailure => c.copy(message = json)
        case e => e
      }
    }
  }

}

class Boot(storage: TracksStorage) extends LazyLogging {

  val algorithmVersion = "4"

  def main(args: Array[String]): Unit = {
    System.setProperty("scala.concurrent.context.numThreads", "x5")
    System.setProperty("scala.concurrent.context.maxThreads", "x10")
    val cfg = Args(args).get
    println(cfg)
    val definitions = storage.findAllReadyToFetch.sortBy(_.updatedAt.toDateTime.getMillis)(Ordering[Long].reverse)
    val filtered = definitions.filter(_.info.matches(cfg.regex))
    doAllWork(filtered, cfg)
  }

  val active = new AtomicInteger()
  val activeDownloads = new AtomicInteger()

  def handleUrl(cfg: Configuration)(implicit definition: Track) = {
    active.incrementAndGet()
    val versionId = definition.UFID + algorithmVersion
    val destination = new File(cfg.destinationDir, s"${definition.title} - ${definition.artist}.mp3")
    if (destination.exists() && cfg.forceLevel < 1 && getUFIDTag(destination).contains(versionId)) {
      logger.info("File {} already downloaded", definition.info)
    } else {
      val work = for {
        a <- download(definition.url, cfg.cacheLocation, cfg.downloaderExec, cfg.debug)
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
        _ <- setTimestamps(finalFile, definition.createdAt, definition.updatedAt)
        _ <- if (definition.rawTitle.contains(ytMeta.title)) ().asRight else storage.updateRawTitle(definition._id, Some(ytMeta.title))
        _ <- if (definition.rawDescription.contains(ytMeta.description)) ().asRight else storage.updateRawDescription(definition._id, Some(ytMeta.title))
        _ = logger.info("File {} ready", definition.info)
      } yield ()

      val result = work.leftMap {
        case t: URLFetchError =>
          logger.warn(s"The track needs to be updated ${t.getMessage}")
          t
        case t: Throwable =>
          logger.error(s"Error $t during handling ${definition.url}, going forward...")
          t
        //        }.valueOr(throw _)
      }
      active.decrementAndGet()
      result
    }

    copyFileToPlaylistDirectories(cfg.destinationDir, destination, definition).valueOr(throw _)
  }

  private def doAllWork(definitions: Vector[Track], cfg: Configuration) {
    val sem = new Semaphore(20, true)
    val id = new AtomicInteger()
    val inc = new Semaphore(1, true)
    import cats.instances.vector._
    import scala.concurrent.duration._
    import cats.syntax.traverse._
    import scala.concurrent.ExecutionContext.Implicits.global
    import cats.instances.future.catsStdInstancesForFuture
    val work = definitions.map { defi =>
      Future {
        sem.acquire()
        handleUrl(cfg)(defi)
        sem.release()
        inc.acquire()
        val thisId = id.incrementAndGet()
        logger.info(s"$thisId/${definitions.size} ready, ${active.get()} active, ${activeDownloads.get()} active downloads")
        inc.release()
      }
    }.sequence
    Await.result(work, 1.day)
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

  def setTimestamps(file: File, created: LocalDateTime, modified: LocalDateTime): Either[Throwable, Unit] = {
    Either.catchNonFatal {
      Files.setAttribute(file.toPath, "creationTime", FileTime.fromMillis(created.toDateTime.getMillis))
      file.setLastModified(modified.toDateTime.getMillis)
    }
  }

  def download(url: String, dest: File, downloaderExec: String, debug: Boolean = false)(implicit track: Track): Either[Throwable, (File, YoutubeDLMeta)] = {

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

    doWork()
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

  def addTags(file: File, author: String, title: String, album: String, id: String): Either[Throwable, String] = {
    Either.catchNonFatal {
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
    }.headOption.getOrElse((start.map(normalizeIt), end.map(normalizeIt)))
  }
}

case class YoutubeDLMeta(
                          description: String,
                          title: String,
                          track: Option[String],
                          artist: Option[String],
                          thumbnail: String,
                          fulltitle: String,
                          _filename: Option[String],
                        )

class URLFetchError(title: String, artist: String, album: String, searchUrl: String)
  extends Throwable(s"cannot fetch $title - $artist ($album), search it again $searchUrl")