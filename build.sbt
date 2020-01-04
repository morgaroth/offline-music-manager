import scala.language.postfixOps
import scala.sys.process._

enablePlugins(JavaAppPackaging, DebianPlugin)

name := "MusicLibrary"

version := "0.1"

scalaVersion := "2.12.10"

val circeVersion = "0.10.0"

resolvers += Resolver.bintrayRepo("morgaroth", "maven")

libraryDependencies ++= Seq(
  "com.github.scopt" %% "scopt" % "3.7.0",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.0",
  "java" % "java-gnome" % "4.1.2" from "file:///usr/share/java/gtk.jar",
  "io.github.morgaroth" %% "utils-mongodb" % "3.0.1",
  "io.morgaroth" %% "gnome-scala" % "1.0.4-SNAPSHOT",
  "org.slf4j" % "slf4j-api" % "2.0.0-alpha1",
) ++ Seq(
  "io.morgaroth" %% "mongodb-testing-docker" % "1.0.1" % Test,
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

sources in(Compile, doc) := Seq.empty

publishArtifact in(Compile, packageDoc) := false

val disabledMainClasses = Set("io.morgaroth.media.library.jobs.GUIApp")

discoveredMainClasses in Compile := {
  (discoveredMainClasses in Compile).value.filterNot(disabledMainClasses)
}