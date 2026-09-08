import scala.sys.process.stringSeqToProcess

val scala3Version = "3.3.3"
val zioVersion = "2.1.6"
val zioHttpVersion = "3.11.4"
val circeVersion = "0.14.9"
val scalaFxVersion = "22.0.0-R33"
val javaFxVersion = "22"

val deploy = taskKey[Unit]("Deploy deb.")
val disabledMainClasses = Set("io.morgaroth.media.library.jobs.GUIApp")

val commonSettings = Seq(
  version := "0.2",
  scalaVersion := scala3Version,
  maintainer := "Mateusz Jaje <mateuszjaje@gmail.com>",
  Compile / doc / sources := Seq.empty,
  Compile / packageDoc / publishArtifact := false,
  scalacOptions ++= Seq(
    "-Xmax-inlines", "64",
    "-feature",
    "-deprecation",
  ),
)

val domain = project.in(file("domain"))
  .settings(commonSettings)
  .settings(
    name := "MusicLibraryDomain",
    libraryDependencies ++= Seq(
      "com.github.scopt" %% "scopt" % "4.1.0",
      "ch.qos.logback" % "logback-classic" % "1.5.6",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
      "dev.zio" %% "zio" % zioVersion,
      "dev.zio" %% "zio-streams" % zioVersion,
      "org.slf4j" % "slf4j-api" % "2.0.13",
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
    name := "MusicLibraryMain",
    libraryDependencies ++= Seq(
      "org.postgresql" % "postgresql" % "42.7.3",
      "com.zaxxer" % "HikariCP" % "5.1.0",
      "org.flywaydb" % "flyway-core" % "10.15.0",
      "org.flywaydb" % "flyway-database-postgresql" % "10.15.0" % Runtime,
      "com.typesafe" % "config" % "1.4.3",
      "org.scalatest" %% "scalatest" % "3.2.19" % Test,
    ),
    debianPackageDependencies += "java17-runtime-headless",
    Compile / discoveredMainClasses := {
      (Compile / discoveredMainClasses).value.filterNot(disabledMainClasses)
    },
    deploy := {
      val outputFile = (Debian / packageBin).toTask.value
      Seq("./deploy.sh", outputFile.getCanonicalPath).!
    },
  )

lazy val http = project.in(file("http"))
  .dependsOn(main)
  .settings(commonSettings)
  .enablePlugins(JavaAppPackaging)
  .settings(
    name := "MusicLibraryHttp",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-http" % zioHttpVersion,
    ),
  )

lazy val osName = System.getProperty("os.name") match {
  case n if n.startsWith("Linux")   => "linux"
  case n if n.startsWith("Mac")     =>
    if (System.getProperty("os.arch") == "aarch64") "mac-aarch64" else "mac"
  case n if n.startsWith("Windows") => "win"
  case _ => throw new Exception("Unknown platform!")
}

val SecondImpl = project.in(file("second-impl"))
  .dependsOn(main)
  .settings(commonSettings)
  .settings(
    name := "MusicLibraryGUI",
    libraryDependencies ++= Seq("base", "controls", "fxml", "graphics", "media", "web").map(m =>
      "org.openjfx" % s"javafx-$m" % javaFxVersion classifier osName
    ),
    libraryDependencies ++= Seq(
      "org.scalafx" %% "scalafx" % scalaFxVersion,
    ),
  )

val root = project.in(file("."))
  .settings(commonSettings)
  .aggregate(domain, main, http, SecondImpl)
  .settings(
    name := "MusicLibrary",
  )
