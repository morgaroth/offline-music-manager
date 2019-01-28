package io.morgaroth.media.library

import java.io.File

import cats.syntax.either._
import io.circe._
import io.circe.generic.auto._
import io.morgaroth.media.library.jobs.MusicDefinition

import scala.io.Source

case class Data(music: Vector[MusicDefinition])

object YamlParser {

  import io.circe.yaml.parser

  def load(path: File): Vector[MusicDefinition] = {
    val yaml = parser.parse(Source.fromFile(path).mkString)
    yaml.leftMap(err => err: Error)
      .flatMap(_.as[Data])
      .valueOr(throw _).music
  }
}

object JsonParser {

  import io.circe.parser.decode

  def load(path: File): Vector[MusicDefinition] = {
    decode[Vector[MusicDefinition]](Source.fromFile(path).mkString).valueOr(throw _)
  }
}