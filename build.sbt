val scala3Version = "3.3.3"
val zioVersion = "2.1.6"
val zioHttpVersion = "3.11.4"
val circeVersion = "0.14.9"

val commonSettings = Seq(
  version := "0.2",
  scalaVersion := scala3Version,
  Compile / doc / sources := Seq.empty,
  Compile / packageDoc / publishArtifact := false,
  scalacOptions ++= Seq(
    "-Xmax-inlines", "64",
    "-feature",
    "-deprecation",
  ),
)

// The shared library: domain model, storage interface, fetcher/executor,
// and CLI Configuration. Dependency-light (no JDBC/HTTP).
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

// The single application: Postgres storage impl, HTTP server, web UI, JSON API,
// and the serve/fetch dispatcher entrypoint (io.morgaroth.media.library.http.HttpApp).
lazy val http = project.in(file("http"))
  .dependsOn(domain)
  .settings(commonSettings)
  .enablePlugins(JavaAppPackaging)
  .settings(
    name := "MusicLibraryHttp",
    maintainer := "Mateusz Jaje <mateuszjaje@gmail.com>",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-http" % zioHttpVersion,
      "org.postgresql" % "postgresql" % "42.7.3",
      "com.zaxxer" % "HikariCP" % "5.1.0",
      "org.flywaydb" % "flyway-core" % "10.15.0",
      "org.flywaydb" % "flyway-database-postgresql" % "10.15.0" % Runtime,
      "com.typesafe" % "config" % "1.4.3",
      "org.scalatest" %% "scalatest" % "3.2.19" % Test,
    ),
  )

val root = project.in(file("."))
  .settings(commonSettings)
  .aggregate(domain, http)
  .settings(
    name := "MusicLibrary",
  )
