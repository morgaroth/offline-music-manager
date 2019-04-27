package io.morgaroth.media.library.jobs

import java.io.File

import cats.syntax.either._
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import io.circe.DecodingFailure
import io.circe.generic.auto._
import io.circe.parser._
import io.morgaroth.media.library.storage.{Track, TracksDB}
import io.morgaroth.media.library.{Args, Configuration}

import scala.sys.process._

case class MusicDefinition(
                            sourceUrl: String,
                            title: String,
                            author: String,
                            draft: Boolean,
                            album: Option[String],
                            startAt: Option[String],
                            endAt: Option[String],
                            fadeOutSeconds: Option[Int],
                          ) {
  def info = s"$author - $title"
}

class Boot extends LazyLogging {
  val algorithmVersion = "2"

  def main(args: Array[String]): Unit = {
    assert(fixDuration(Some("0:05"), Some("4:00"))._2.contains("00:03:55"))

    val tcfg = ConfigFactory.load()
    val mongoCfg = tcfg.getConfig("music-library.mongo")
    val storage = new TracksDB(mongoCfg)
    val cfg = Args(args).get
    println(cfg)
    //    val definitions: Vector[MusicDefinition] = YamlParser.load(cfg.definitionsFile)
    val definitions = storage.all
    doAllWork(definitions, cfg)

  }

  def doAllWork(definitions: Vector[Track], cfg: Configuration) = {
    definitions.foreach { definition =>
      val versionId = definition.UFID + algorithmVersion
      val destination = new File(cfg.destinationDir, s"${definition.title} - ${definition.artist}.mp3")
      if (destination.exists() && cfg.forceLevel < 1 && getUFIDTag(destination).contains(versionId)) {
        logger.info("File {} already downloaded", definition.info)
      } else {
        try {
          val meta = download(definition.url, cfg.cacheLocation, cfg.downloaderExec).valueOr { x => print(x); throw x }
          val source = new File(meta._filename)
          val mp3file = convertToMP3(source, cfg, definition)
          val trimmed = strip(mp3file, definition)
          val finalFile = dist(trimmed, destination)
          addTags(
            album = "Twórczość",
            author = definition.artist,
            title = definition.title,
            file = finalFile,
            id = versionId,
          )
          logger.info("File {} ready", definition.info)
        } catch {
          case t: Throwable => logger.error(s"Error $t during handling ${definition.url}, going forward...")
        }
      }
    }
  }

  def download(url: String, dest: File, downloaderExec: String) = {
    def doWork(retries: Int = 5): String = {
      try {
        Seq(downloaderExec, "--print-json", "--restrict-filenames", "-f", "mp4",
          "-o", s"${dest.getAbsolutePath}/%(title)s (%(id)s) - RAW.%(ext)s", url).!!<
      } catch {
        case e: Throwable if retries > 0 =>
          logger.warn(s"error during downloading link $url")
          doWork(retries - 1)
      }
    }

    val json = doWork()
    decode[YoutubeDLMeta](json).left.map {
      case c: DecodingFailure => c.copy(message = json)
      case e => e
    }
  }

  def ffmpeg(inputArgs: String*)(input: File)(outputArgs: String*)(output: File)(quiet: Boolean = true, debug: Boolean = true) = {
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

  def findMaxValue(file: File) = {
    val output = ffmpeg()(file)("-af", "volumedetect", "-sn", "-dn", "-f", "null")(new File("/dev/null"))(quiet = false)
    val maxVolumeLine = output.split("\n").filter(_.contains("max_volume:"))
    assert(maxVolumeLine.length == 1)
    val value = maxVolumeLine.head.split(" ")(4)
    value.toDouble
  }


  def convertToMP3(source: File, config: Configuration, track: Track) = {
    val target = new File(source.getParent, source.getName.split("""\.""").init.mkString + ".mp3")
    if (!target.exists() || config.forceLevel > 1) {
      val maxVolume = Option(Option(findMaxValue(source)).map(_ * -1).getOrElse(0d) + track.volumeChange.map(_.toDouble).getOrElse(0d)).filter(_ != 0)
      target.delete()
      val reVolumeArgs = maxVolume.map(x => Seq("-af", s"volume=${x}dB")).getOrElse(Seq.empty)
      ffmpeg()(source)(reVolumeArgs ++ Seq("-q:a", "0", "-acodec", "libmp3lame"): _*)(target)()
    }
    target
  }

  def strip(source: File, definition: Track) = {
    val t1 = new File(source.getParentFile, "TMP - " + source.getName)
    t1.delete()
    t1.deleteOnExit()
    val (a, b) = fixDuration(definition.startAt, definition.endAt)
    val codecsInfo = definition.fadeOutSeconds.map { secs =>
      val diff1 = b.get.split(":").map(_.toInt)
      val targetTrackSeconds = diff1.head * 3600 + diff1(1) * 60 + diff1(2)
      Seq("-filter_complex", s"afade=t=out:st=${targetTrackSeconds - secs - 1}:d=$secs", "-q:a", "0", "-acodec", "libmp3lame")
    }.getOrElse(Seq("-acodec", "copy"))
    ffmpeg(a.map("-ss" :: _.toString :: Nil).toSeq.flatten: _*)(source)(
      codecsInfo ++ b.map("-to" :: _.toString :: Nil).toSeq.flatten: _*,
    )(t1)()
    t1
  }

  def dist(source: File, destination: File) = {
    destination.delete()
    assert(source.renameTo(destination), "cannot move")
    destination
  }

  def addTags(file: File, author: String, title: String, album: String, id: String): Unit = {
    Seq("mid3v2", file.getAbsolutePath, "-t", title, "-a", author, "-A", album, "--UFID", s"definitionhash:$id").!!
  }

  def getUFIDTag(file: File): Option[String] = {
    Seq("mid3v2", file.getAbsolutePath, "--list").!!.split("\n").find(_.startsWith("UFID=definitionhash=")).map(_.stripPrefix("UFID=definitionhash=").filterNot(_ == '\'').trim)
  }

  def normalizeIt(input: String) = {
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

case class YoutubeDLMeta(_filename: String)