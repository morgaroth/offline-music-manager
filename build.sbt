import scala.language.postfixOps
import sys.process._

enablePlugins(JavaAppPackaging, DebianPlugin)

name := "MusicLibrary"

version := "0.1"

scalaVersion := "2.12.7"

val circeVersion = "0.10.0"

libraryDependencies ++= Seq(
  "com.github.scopt" %% "scopt" % "3.7.0",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.0",
  "org.snakeyaml" % "snakeyaml-engine" % "1.0",
  "io.circe" %% "circe-yaml" % "0.9.0",
  "io.github.morgaroth" %% "utils-mongodb" % "3.0.1",
) ++ Seq(
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser",
).map(_ % circeVersion)

maintainer := "Mateusz Jaje <mateuszjaje@gmail.com"

debianPackageDependencies += "java8-runtime-headless"

val deploy = taskKey[Unit]("Deploy deb.")

deploy := {
  (packageBin in Debian).toTask.value
  Seq("./deploy.sh", s"${target.value.getAbsolutePath}/${name.value}_${version.value}_all.deb").!
}
