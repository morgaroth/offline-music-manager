package io.morgaroth.media.library.jobs

import io.circe.parser.*
import io.circe.DecodingFailure

import scala.sys.process.*
import scala.util.Try

object MetaDataFetcher:

  def getMetadata(url: String): Either[Throwable, YoutubeDLMeta] =
    val cfg = io.morgaroth.media.library.Configuration()
    getYTMetadata(cfg.downloaderExec, url, None, cfg.cookieArgs)

  def getYTMetadata(
    downloaderExec: String,
    url: String,
    format: Option[String],
    cookieArgs: Seq[String] = Seq.empty,
  ): Either[Throwable, YoutubeDLMeta] =
    Try {
      (Seq(downloaderExec) ++ cookieArgs ++ Seq("--print-json", "-s", url) ++ format.toSeq.flatMap(x => Seq("-o", x))).!!
    }.toEither.flatMap { json =>
      decode[YoutubeDLMeta](json).left.map:
        case c: DecodingFailure => c.withMessage(json)
        case e => e
    }
