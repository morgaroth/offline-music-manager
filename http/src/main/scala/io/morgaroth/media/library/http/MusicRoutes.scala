package io.morgaroth.media.library.http

import io.circe.syntax.*
import io.circe.{Json, parser}
import io.morgaroth.media.library.http.JsonCodecs.given
import io.morgaroth.media.library.storage.{Track, TrackStatus, ZioTracksStorageService}
import zio.*
import zio.http.*

import java.util.UUID

/** HTTP surface over the existing ZIO domain services.
  *
  * Read/edit endpoints are synchronous (they map 1:1 to storage methods).
  * Fetch is asynchronous: it returns a job id immediately and work runs on a
  * daemon fiber, pollable via the jobs endpoints.
  */
class MusicRoutes(storage: ZioTracksStorageService, jobs: JobRegistry):

  private def json(j: Json): Response =
    Response.json(j.noSpaces)

  private def trackResponse(t: Track): Response = json(t.asJson)
  private def tracksResponse(ts: Vector[Track]): Response = json(ts.asJson)

  private def badRequest(msg: String): Response =
    Response.json(Json.obj("error" -> msg.asJson).noSpaces).status(Status.BadRequest)

  /** Read a JSON body and pull a single string field out of it. */
  private def stringField(req: Request, field: String): Task[String] =
    for
      raw <- req.body.asString
      js <- ZIO.fromEither(parser.parse(raw))
      value <- ZIO.fromOption(js.hcursor.get[String](field).toOption)
        .orElseFail(IllegalArgumentException(s"missing string field '$field'"))
    yield value

  private def optStringField(req: Request, field: String): Task[Option[String]] =
    for
      raw <- req.body.asString
      js <- ZIO.fromEither(parser.parse(raw))
    yield js.hcursor.get[String](field).toOption.filter(_.nonEmpty)

  private val uiResponse: Response =
    Response(
      status = Status.Ok,
      headers = Headers(Header.ContentType(MediaType.text.html)),
      body = Body.fromString(WebUi.page),
    )

  val routes: Routes[Any, Response] =
    Routes(
      // Web UI.
      Method.GET / Root -> handler(uiResponse),

      // Health check.
      Method.GET / "health" -> Handler.ok,

      // List / search tracks. Query params: artist, title, status, q (generic), page, limit.
      Method.GET / "tracks" -> handler { (req: Request) =>
        val artist = req.queryParam("artist").filter(_.nonEmpty)
        val title = req.queryParam("title").filter(_.nonEmpty)
        val statusParam = req.queryParam("status").filter(_.nonEmpty)
        val generic = req.queryParam("q").filter(_.nonEmpty)
        val page = req.queryParam("page").flatMap(_.toIntOption).getOrElse(1)
        val limit = req.queryParam("limit").flatMap(_.toIntOption)

        val effect =
          generic match
            case Some(text) =>
              storage.genericSearch(text, page).map(tracksResponse)
            case None =>
              val statuses = statusParam.flatMap(s => TrackStatus.byDbRepr.get(s.toLowerCase)).map(Set(_))
              storage
                .search(artist, title, statuses, limit.map(Integer.valueOf).orNull)
                .map(tracksResponse)

        effect.orElseFail(Response.internalServerError)
      },

      // Get one track by id.
      Method.GET / "tracks" / uuid("id") -> handler { (id: UUID, _: Request) =>
        storage.getById(id).map(trackResponse).orElseFail(Response.notFound(s"track $id"))
      },

      // Create a track from a URL. Body: { "url": "..." }.
      Method.POST / "tracks" -> handler { (req: Request) =>
        (for
          url <- stringField(req, "url")
          track <- storage.store(url)
        yield trackResponse(track).status(Status.Created))
          .catchAll(t => ZIO.succeed(badRequest(t.getMessage)))
      },

      // Update endpoints. Each takes a JSON body with the relevant field.
      Method.PATCH / "tracks" / uuid("id") / "artist" -> handler { (id: UUID, req: Request) =>
        updateWith(req, "artist")(v => storage.updateArtist(id, v))
      },
      Method.PATCH / "tracks" / uuid("id") / "title" -> handler { (id: UUID, req: Request) =>
        updateWith(req, "title")(v => storage.updateTitle(id, v))
      },
      Method.PATCH / "tracks" / uuid("id") / "album" -> handler { (id: UUID, req: Request) =>
        updateWith(req, "album")(v => storage.updateAlbum(id, v))
      },
      Method.PATCH / "tracks" / uuid("id") / "url" -> handler { (id: UUID, req: Request) =>
        updateWith(req, "url")(v => storage.updateUrl(id, v))
      },
      Method.PATCH / "tracks" / uuid("id") / "status" -> handler { (id: UUID, req: Request) =>
        (for
          raw <- stringField(req, "status")
          status <- ZIO.fromOption(TrackStatus.byDbRepr.get(raw.toLowerCase))
            .orElseFail(IllegalArgumentException(s"unknown status '$raw'"))
          track <- storage.updateStatus(id, status)
        yield trackResponse(track))
          .catchAll(t => ZIO.succeed(badRequest(t.getMessage)))
      },
      Method.PATCH / "tracks" / uuid("id") / "start-at" -> handler { (id: UUID, req: Request) =>
        updateOptional(req, "startAt")(v => storage.updateStartAt(id, v))
      },
      Method.PATCH / "tracks" / uuid("id") / "end-at" -> handler { (id: UUID, req: Request) =>
        updateOptional(req, "endAt")(v => storage.updateEndAt(id, v))
      },

      // Playlists: map of playlist name -> tracks.
      Method.GET / "playlists" -> handler { (_: Request) =>
        storage
          .findAllPlaylists()
          .map(m => json(m.view.mapValues(_.asJson).toMap.asJson))
          .orElseFail(Response.internalServerError)
      },

      // Trigger an async fetch for a track. Query params: force (int), debug (bool).
      Method.POST / "tracks" / uuid("id") / "fetch" -> handler { (id: UUID, req: Request) =>
        val force = req.queryParam("force").flatMap(_.toIntOption).getOrElse(0)
        val debug = req.queryParam("debug").exists(v => v == "true" || v == "1")
        jobs
          .submit(id, force, debug)
          .map(status => json(jobJson(status)).status(Status.Accepted))
          .orElseFail(Response.internalServerError)
      },

      // Poll a job by id.
      Method.GET / "jobs" / uuid("jobId") -> handler { (jobId: UUID, _: Request) =>
        jobs.get(jobId).map {
          case Some(status) => json(jobJson(status))
          case None => Response.notFound(s"job $jobId")
        }
      },

      // List recent jobs.
      Method.GET / "jobs" -> handler { (_: Request) =>
        jobs.list.map(js => json(Json.arr(js.map(jobJson)*)))
      },
    )

  private def updateWith(req: Request, field: String)(f: String => Task[Track]): ZIO[Any, Response, Response] =
    (for
      value <- stringField(req, field)
      track <- f(value)
    yield trackResponse(track))
      .catchAll(t => ZIO.succeed(badRequest(t.getMessage)))

  private def updateOptional(req: Request, field: String)(f: Option[String] => Task[Track]): ZIO[Any, Response, Response] =
    (for
      value <- optStringField(req, field)
      track <- f(value)
    yield trackResponse(track))
      .catchAll(t => ZIO.succeed(badRequest(t.getMessage)))

  private def jobJson(status: JobStatus): Json =
    Json.obj(
      "jobId" -> status.jobId.toString.asJson,
      "trackId" -> status.trackId.toString.asJson,
      "state" -> status.state.toString.toLowerCase.asJson,
      "startedAt" -> status.startedAt.toString.asJson,
      "finishedAt" -> status.finishedAt.map(_.toString).asJson,
      "error" -> status.error.asJson,
    )

object MusicRoutes:
  val live: ZLayer[ZioTracksStorageService & JobRegistry, Nothing, MusicRoutes] =
    ZLayer.fromFunction(MusicRoutes(_, _))
