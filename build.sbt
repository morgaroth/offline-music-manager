import scala.language.postfixOps
import scala.sys.process._

enablePlugins(JavaAppPackaging, DebianPlugin)

name := "MusicLibrary"

version := "0.1"

scalaVersion := "2.12.8"

val circeVersion = "0.11.1"

resolvers += Resolver.bintrayRepo("morgaroth", "maven")

libraryDependencies ++= Seq(
  "com.github.scopt" %% "scopt" % "3.7.0",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.0",
  "org.snakeyaml" % "snakeyaml-engine" % "1.0",
  //  "io.github.morgaroth" %% "utils-mongodb" % "3.0.1",
  "com.typesafe" % "config" % "1.3.4",
  "org.mongodb.scala" %% "mongo-scala-driver" % "2.6.0",

  "io.morgaroth" %% "gnome-scala" % "1.0.3",
) ++ Seq(
  "io.morgaroth" %% "mongodb-testing-docker" % "2.0.0-SNAPSHOT" % Test,
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
