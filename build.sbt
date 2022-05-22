import scala.language.postfixOps
import scala.sys.process.stringSeqToProcess
import KeyRanks.Invisible

val commonSettings = Seq(
  version := "0.1",
  scalaVersion := "2.13.8",
  maintainer.withRank(Invisible) := "Mateusz Jaje <mateuszjaje@gmail.com",
)

val circeVersion = "0.12.0"
val deploy = taskKey[Unit]("Deploy deb.")
val disabledMainClasses = Set("io.morgaroth.media.library.jobs.GUIApp")

val domain = project.in(file("domain"))
  .settings(commonSettings)
  .settings(
    name := "MusicLibraryDomain",

    libraryDependencies ++= Seq(
      "com.github.scopt" %% "scopt" % "3.7.1",
      "ch.qos.logback" % "logback-classic" % "1.3.0-alpha5",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.4",
      "java" % "java-gnome" % "4.1.3" from "file:///usr/share/java/gtk.jar",
      "io.gitlab.mateuszjaje" %% "gnome-scala" % "1.2.0",
      "dev.zio" %% "zio" % "1.0.3",
      "org.slf4j" % "slf4j-api" % "2.0.0-alpha1",
    ) ++ Seq(
      "io.circe" %% "circe-generic",
      "io.circe" %% "circe-parser",
    ).map(_ % circeVersion),
  )

lazy val `deprecated-main` = project.in(file("deprecated-main"))
  .dependsOn(domain)
  .settings(commonSettings)
  .enablePlugins(JavaAppPackaging, DebianPlugin)
  .settings(
    maintainer := "Mateusz Jaje <mateuszjaje@gmail.com",
    debianPackageDependencies += "java11-runtime-headless",

    libraryDependencies ++= Seq(
      //      "io.github.morgaroth" %% "utils-mongodb" % "3.0.1",
      //      "io.morgaroth" %% "mongodb-testing-docker" % "1.0.1" % Test,
    ),

    //    deploy := {
    //      (packageBin in Debian).toTask.value
    //      Seq("./deploy.sh", s"${target.value.getAbsolutePath}/${name.value}_${version.value}_all.deb").!
    //    },

    Compile / doc / sources := Seq.empty,
    Compile / packageDoc / publishArtifact := false,

    Compile / discoveredMainClasses := {
      (Compile / discoveredMainClasses).value.filterNot(disabledMainClasses)
    },
  )

lazy val main = project.in(file("main"))
  .dependsOn(domain)
  .settings(commonSettings)
  .enablePlugins(JavaAppPackaging, DebianPlugin)
  .settings(
    libraryDependencies ++= Seq(
      "io.github.kitlangton" %% "zio-magic" % "0.3.12",
      "org.mongodb.scala" %% "mongo-scala-driver" % "4.0.5",
      "org.scalatest" %% "scalatest" % "3.+" % Test,
      "com.typesafe" % "config" % "1.4.2",
    ),
    maintainer := "Mateusz Jaje <mateuszjaje@gmail.com",
    debianPackageDependencies += "java11-runtime-headless",
    Compile / doc / sources := Seq.empty,
    Compile / packageDoc / publishArtifact := false,
    Compile / discoveredMainClasses := {
      (Compile / discoveredMainClasses).value.filterNot(disabledMainClasses)
    },
    deploy := {
      val outputFile = (Debian / packageBin).toTask.value
      Seq("./deploy.sh", outputFile.getCanonicalPath).!
    },
  )

val root = project.in(file("."))
  .settings(commonSettings)
  .aggregate(main, /*`deprecated-main`,*/ domain)
  .settings(
    name := "MusicLibrary",
  )