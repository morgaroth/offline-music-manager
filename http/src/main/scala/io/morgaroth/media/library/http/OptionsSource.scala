package io.morgaroth.media.library.http

import io.circe.Json
import io.circe.parser

import java.io.File
import scala.io.Source
import scala.util.Try

/** Unified configuration source that makes the image HA-agnostic.
  *
  * Precedence for every key:
  *   1. `/data/options.json` — written by the Home Assistant Supervisor from the
  *      add-on's options schema (present only when run as an add-on).
  *   2. `MUSIC_LIBRARY_*` environment variable — the standalone `docker run` path.
  *   3. built-in default.
  *
  * Nothing here is HA-specific beyond knowing the file path; a plain
  * `docker run -e MUSIC_LIBRARY_...` works without the file, and the add-on works
  * without any env wiring.
  */
final class OptionsSource(private val options: Map[String, Json]):

  private def fromOptions(key: String): Option[String] =
    options.get(key).flatMap { j =>
      j.asString
        .orElse(j.asNumber.map(_.toString))
        .orElse(j.asBoolean.map(_.toString))
    }.map(_.trim).filter(_.nonEmpty)

  private def fromEnv(envKey: String): Option[String] =
    sys.env.get(envKey).map(_.trim).filter(_.nonEmpty)

  /** Look up by add-on option key and/or env var name. */
  def str(optionKey: String, envKey: String): Option[String] =
    fromOptions(optionKey).orElse(fromEnv(envKey))

  def string(optionKey: String, envKey: String, default: String): String =
    str(optionKey, envKey).getOrElse(default)

  def int(optionKey: String, envKey: String, default: Int): Int =
    str(optionKey, envKey).flatMap(_.toIntOption).getOrElse(default)

  def bool(optionKey: String, envKey: String, default: Boolean): Boolean =
    str(optionKey, envKey)
      .map(v => v == "true" || v == "1" || v.equalsIgnoreCase("yes"))
      .getOrElse(default)

object OptionsSource:
  val OptionsPath = "/data/options.json"

  /** Load `/data/options.json` if present; otherwise an empty source (env only). */
  def load(path: String = OptionsPath): OptionsSource =
    val opts =
      val f = File(path)
      if !f.isFile then Map.empty[String, Json]
      else
        val parsed =
          for
            raw <- Try(Source.fromFile(f, "UTF-8").mkString).toEither
            json <- parser.parse(raw)
            obj <- json.asObject.toRight(new RuntimeException("options.json is not a JSON object"))
          yield obj.toMap
        parsed.getOrElse(Map.empty)
    OptionsSource(opts)
