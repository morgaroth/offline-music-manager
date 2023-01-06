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
//      "java" % "java-gnome" % "4.1.3" from "file:///usr/share/java/gtk.jar",
//      "io.gitlab.mateuszjaje" %% "gnome-scala" % "1.2.0",
      "dev.zio" %% "zio" % "1.0.3",
      "org.slf4j" % "slf4j-api" % "2.0.0-alpha1",
    ) ++ Seq(
      "io.circe" %% "circe-generic",
      "io.circe" %% "circe-parser",
    ).map(_ % circeVersion),
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

lazy val osName = System.getProperty("os.name") match {
  case n if n.startsWith("Linux") => "linux"
  case n if n.startsWith("Mac") => "mac"
  case n if n.startsWith("Windows") => "win"
  case _ => throw new Exception("Unknown platform!")
}
val SecondImpl = project.in(file("second-impl"))
  .dependsOn(main)
  .settings(commonSettings)
  .settings(
    idePackagePrefix.withRank(Invisible) := Some("io.gitlab.mateuszjaje.offlinemusiclibrary"),
    Compile / doc / sources := Seq.empty,
    Compile / packageDoc / publishArtifact := false,
    libraryDependencies ++= Seq("base", "controls", "fxml", "graphics", "media", "swing", "web").map(m =>
      "org.openjfx" % s"javafx-$m" % "18" classifier osName
    ),
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % "1.+",
      "org.scalafx" %% "scalafx" % "18.+",
    )
  )

val root = project.in(file("."))
  .settings(commonSettings)
  .aggregate(main, domain, SecondImpl)
  .settings(
    name := "MusicLibrary",
  )