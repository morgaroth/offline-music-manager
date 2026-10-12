package io.morgaroth.media.library.http

import io.circe.syntax.*
import io.circe.{Json, parser}
import io.morgaroth.media.library.http.JsonCodecs.given
import io.morgaroth.media.library.jobs.MetaDataFetcher
import io.morgaroth.media.library.storage.{MediaType as TrackMediaType, Track, TrackStatus, ZioTracksStorageService}
import zio.*
import zio.http.*

import java.util.UUID

/** HTTP surface over the existing ZIO domain services.
  *
  * Read/edit endpoints are synchronous (they map 1:1 to storage methods).
  * Fetch is asynchronous: it returns a job id immediately and work runs on a
  * daemon fiber, pollable via the jobs endpoints.
  */
class MusicRoutes(storage: ZioTracksStorageService, serverConfig: ServerConfig, queue: io.morgaroth.media.library.storage.FetchQueueService):

  private def json(j: Json): Response =
    Response.json(j.noSpaces)

  private def trackResponse(t: Track): Response = json(t.asJson)
  private def tracksResponse(ts: Vector[Track]): Response = json(ts.asJson)

  // yt-dlp cookie args from the configured cookies file (empty when unset).
  private val cookieArgs: Seq[String] =
    serverConfig.cookiesFile.filter(_.nonEmpty).toSeq.flatMap(f => Seq("--cookies", f))

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

      // Hard-delete a track.
      Method.DELETE / "tracks" / uuid("id") -> handler { (id: UUID, _: Request) =>
        storage.delete(id)
          .as(Response.status(Status.NoContent))
          .orElseFail(Response.notFound(s"track $id"))
      },

      // YouTube metadata for a track's URL (duration, uploader, thumbnail, raw
      // title/description) — runs yt-dlp metadata-only (no download). Used by the
      // Edit page side panel to help decide trim/settings.
      Method.GET / "tracks" / uuid("id") / "metadata" -> handler { (id: UUID, _: Request) =>
        (for
          track <- storage.getById(id)
          meta <- ZIO.fromEither(
            MetaDataFetcher.getYTMetadata(serverConfig.downloaderExec, track.url, None, cookieArgs)
          )
          // Persist raw title/description ONLY when the fetch returned a
          // non-empty value that differs. The raw_* columns preserve the real
          // source metadata so a dead URL can be re-sourced later, so we never
          // overwrite a good stored value with an empty/blank one (yt-dlp can
          // exit 0 yet return empty fields for gated/changed pages).
          newTitle = meta.title.trim
          newDesc = meta.description.trim
          _ <- storage.updateRawTitle(id, newTitle).when(newTitle.nonEmpty && newTitle != track.rawTitle)
          _ <- storage.updateRawDescription(id, newDesc).when(newDesc.nonEmpty && newDesc != track.rawDescription)
        yield json(Json.obj(
          "title" -> meta.title.asJson,
          "fullTitle" -> meta.fulltitle.asJson,
          "description" -> meta.description.asJson,
          "uploader" -> meta.uploader.asJson,
          "uploadDate" -> meta.upload_date.asJson,
          "thumbnail" -> meta.thumbnail.asJson,
          "durationSeconds" -> meta.duration.asJson,
          "durationString" -> meta.duration_string.asJson,
          "webpageUrl" -> meta.webpage_url.asJson,
        )))
          .catchAll(t => ZIO.succeed(badRequest(s"metadata lookup failed: ${t.getMessage}")))
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
          // A track may only become `final` once it has usable metadata:
          // title, artist, album, and at least one playlist.
          _ <- ZIO.when(status == TrackStatus.Final):
            storage.getById(id).flatMap: t =>
              val missing = Vector(
                Option.when(t.title.trim.isEmpty)("title"),
                Option.when(t.artist.trim.isEmpty)("artist"),
                Option.when(t.album.trim.isEmpty)("album"),
                Option.when(t.playlists.filter(_.nonEmpty).isEmpty)("playlists"),
              ).flatten
              ZIO.when(missing.nonEmpty):
                ZIO.fail(IllegalArgumentException(s"cannot set final: missing ${missing.mkString(", ")}"))
          track <- storage.updateStatus(id, status)
        yield trackResponse(track))
          .catchAll(t => ZIO.succeed(badRequest(t.getMessage)))
      },
      // Media type: music | video.
      Method.PATCH / "tracks" / uuid("id") / "type" -> handler { (id: UUID, req: Request) =>
        (for
          raw <- stringField(req, "type")
          mt <- ZIO.fromOption(TrackMediaType.byDbRepr.get(raw.toLowerCase))
            .orElseFail(IllegalArgumentException(s"unknown media type '$raw'"))
          track <- storage.updateMediaType(id, mt)
        yield trackResponse(track))
          .catchAll(t => ZIO.succeed(badRequest(t.getMessage)))
      },
      Method.PATCH / "tracks" / uuid("id") / "start-at" -> handler { (id: UUID, req: Request) =>
        updateOptional(req, "startAt")(v => storage.updateStartAt(id, v))
      },
      Method.PATCH / "tracks" / uuid("id") / "end-at" -> handler { (id: UUID, req: Request) =>
        updateOptional(req, "endAt")(v => storage.updateEndAt(id, v))
      },
      // Fade-out seconds: integer, or empty/absent to clear.
      Method.PATCH / "tracks" / uuid("id") / "fade-out-seconds" -> handler { (id: UUID, req: Request) =>
        (for
          raw <- optStringField(req, "fadeOutSeconds")
          parsed <- raw match
            case None => ZIO.succeed(None)
            case Some(s) => ZIO.fromOption(s.toIntOption).map(Some(_))
              .orElseFail(IllegalArgumentException(s"invalid integer '$s'"))
          track <- storage.updateFadeOutSeconds(id, parsed)
        yield trackResponse(track))
          .catchAll(t => ZIO.succeed(badRequest(t.getMessage)))
      },
      // Volume change (dB): decimal, or empty/absent to clear.
      Method.PATCH / "tracks" / uuid("id") / "volume-change" -> handler { (id: UUID, req: Request) =>
        (for
          raw <- optStringField(req, "volumeChange")
          parsed <- raw match
            case None => ZIO.succeed(None)
            case Some(s) => ZIO.attempt(Some(BigDecimal(s)))
              .orElseFail(IllegalArgumentException(s"invalid number '$s'"))
          track <- storage.updateVolumeChange(id, parsed)
        yield trackResponse(track))
          .catchAll(t => ZIO.succeed(badRequest(t.getMessage)))
      },
      // Playlists: JSON array of names, e.g. { "playlists": ["a","b"] }.
      Method.PATCH / "tracks" / uuid("id") / "playlists" -> handler { (id: UUID, req: Request) =>
        (for
          raw <- req.body.asString
          js <- ZIO.fromEither(parser.parse(raw))
          names <- ZIO.fromOption(js.hcursor.get[Set[String]]("playlists").toOption)
            .orElseFail(IllegalArgumentException("missing array field 'playlists'"))
          track <- storage.updatePlaylists(id, names.filter(_.nonEmpty))
        yield trackResponse(track))
          .catchAll(t => ZIO.succeed(badRequest(t.getMessage)))
      },

      // Playlists: map of playlist name -> tracks.
      Method.GET / "playlists" -> handler { (_: Request) =>
        storage
          .findAllPlaylists()
          .map(m => json(m.view.mapValues(_.asJson).toMap.asJson))
          .orElseFail(Response.internalServerError)
      },

      // "Fetch now" from the Edit page: enqueue this single track at HIGH
      // priority (1) so it jumps ahead of the normal batch (priority 0). Goes
      // through the same durable queue + orchestrator as the Fetch tab.
      Method.POST / "tracks" / uuid("id") / "fetch" -> handler { (id: UUID, _: Request) =>
        (for
          _ <- storage.getById(id) // 404 if the track doesn't exist
          added <- queue.enqueue(Vector(id), priority = 1)
          st <- queue.stats
        yield json(Json.obj("added" -> added.asJson, "stats" -> statsJson(st))).status(Status.Accepted))
          .orElseFail(Response.notFound(s"track $id"))
      },

      // --- Durable fetch queue ---

      // Enqueue all FINAL tracks matching the query (?q= text, ?type= music|video).
      // Only `final` tracks qualify regardless of query.
      Method.POST / "fetch-queue" -> handler { (req: Request) =>
        val text = req.queryParam("q").map(_.trim).filter(_.nonEmpty)
        val mt = req.queryParam("type").flatMap(s => TrackMediaType.byDbRepr.get(s.toLowerCase))
        (for
          matched <- storage.findForFetch(text, mt)
          added <- queue.enqueue(matched.map(_._id), priority = 0)
          st <- queue.stats
        yield json(Json.obj(
          "matched" -> matched.size.asJson,
          "added" -> added.asJson,
          "stats" -> statsJson(st),
        )).status(Status.Accepted))
          .orElseFail(Response.internalServerError)
      },

      // Queue stats + rows (for the progress view).
      Method.GET / "fetch-queue" -> handler { (_: Request) =>
        (for
          st <- queue.stats
          items <- queue.list
        yield json(Json.obj(
          "stats" -> statsJson(st),
          "items" -> Json.arr(items.map(queueItemJson)*),
        )))
          .orElseFail(Response.internalServerError)
      },

      // Kill: remove pending rows (running ones finish, then cleanup empties).
      Method.DELETE / "fetch-queue" / "pending" -> handler { (_: Request) =>
        queue.clearPending.map(n => json(Json.obj("removed" -> n.asJson)))
          .orElseFail(Response.internalServerError)
      },

      // Admin: clear errored rows.
      Method.DELETE / "fetch-queue" / "errored" -> handler { (_: Request) =>
        queue.clearErrored.map(n => json(Json.obj("removed" -> n.asJson)))
          .orElseFail(Response.internalServerError)
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

  private def statsJson(s: io.morgaroth.media.library.storage.FetchQueueStats): Json =
    Json.obj(
      "pending" -> s.pending.asJson,
      "running" -> s.running.asJson,
      "done" -> s.done.asJson,
      "errored" -> s.errored.asJson,
      "total" -> s.total.asJson,
    )

  private def queueItemJson(i: io.morgaroth.media.library.storage.FetchQueueItem): Json =
    Json.obj(
      "id" -> i.id.toString.asJson,
      "trackId" -> i.trackId.toString.asJson,
      "status" -> i.status.dbRepr.asJson,
      "priority" -> i.priority.asJson,
      "startedAt" -> i.startedAt.map(_.toString).asJson,
      "error" -> i.error.asJson,
      "title" -> i.title.asJson,
      "artist" -> i.artist.asJson,
      "type" -> i.mediaType.asJson,
    )

object MusicRoutes:
  val live: ZLayer[ZioTracksStorageService & ServerConfig & io.morgaroth.media.library.storage.FetchQueueService, Nothing, MusicRoutes] =
    ZLayer.fromFunction(MusicRoutes(_, _, _))
