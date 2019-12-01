import scala.language.postfixOps
import scala.sys.process._

enablePlugins(JavaAppPackaging, DebianPlugin)

name := "MusicLibrary"

version := "0.1"

scalaVersion := "2.12.9"

resolvers += Resolver.bintrayRepo("morgaroth", "maven")

libraryDependencies ++= Seq(
  "com.github.scopt" %% "scopt" % "3.7.0",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.0",
  "io.github.morgaroth" %% "utils-mongodb" % "3.0.1",
  "io.morgaroth" %% "gnome-scala" % "1.0.3",
) ++ Seq(
  "io.morgaroth" %% "mongodb-testing-docker" % "1.0.1" % Test,
)

maintainer := "Mateusz Jaje <mateuszjaje@gmail.com"

debianPackageDependencies += "java8-runtime-headless"

val deploy = taskKey[Unit]("Deploy deb.")

deploy := {
  (packageBin in Debian).toTask.value
  Seq("./deploy.sh", s"${target.value.getAbsolutePath}/${name.value}_${version.value}_all.deb").!
}
