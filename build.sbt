import scala.language.postfixOps
import scala.sys.process.stringSeqToProcess

val commonSettings = Seq(
  version := "0.1",
  scalaVersion := "2.12.12",
)

val circeVersion = "0.12.0"
val deploy = taskKey[Unit]("Deploy deb.")
val disabledMainClasses = Set("io.morgaroth.media.library.jobs.GUIApp")

val domain = project.in(file("domain"))
  .settings(commonSettings)
  .settings(
    name := "MusicLibraryDomain",
    resolvers += "Artifactory" at "https://mateuszjajedev.jfrog.io/artifactory/maven/",

    libraryDependencies ++= Seq(
      "com.github.scopt" %% "scopt" % "3.7.1",
      "ch.qos.logback" % "logback-classic" % "1.3.0-alpha5",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.0",
      "java" % "java-gnome" % "4.1.3" from "file:///usr/share/java/gtk.jar",
      "io.morgaroth" %% "gnome-scala" % "1.1.1",
      "org.slf4j" % "slf4j-api" % "2.0.0-alpha1",
    ) ++ Seq(
      "io.circe" %% "circe-generic",
      "io.circe" %% "circe-parser",
    ).map(_ % circeVersion),
  )

lazy val `deprecated-main` = project.in(file("deprecated-main"))
  .dependsOn(domain)
  .enablePlugins(JavaAppPackaging, DebianPlugin)
  .settings(
    maintainer := "Mateusz Jaje <mateuszjaje@gmail.com",
    debianPackageDependencies += "java11-runtime-headless",

    libraryDependencies ++= Seq(
      "io.github.morgaroth" %% "utils-mongodb" % "3.0.1",
      "io.morgaroth" %% "mongodb-testing-docker" % "1.0.1" % Test,
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
  .enablePlugins(JavaAppPackaging, DebianPlugin)
  .settings(
    libraryDependencies ++= Seq(
      "org.mongodb.scala" %% "mongo-scala-driver" % "4.0.5",
      "org.scalatest" %% "scalatest" % "3.+" % Test,
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
  .aggregate(main, /*`deprecated-main`,*/ domain)
  .settings(
    name := "MusicLibrary",
  )