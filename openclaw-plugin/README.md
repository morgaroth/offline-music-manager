# Offline Music Library — OpenClaw plugin

A thin [OpenClaw](https://github.com/openclaw/openclaw) plugin that exposes the
offline music library to an AI agent. It registers agent tools that call the
library's HTTP API (the `http` sbt module, `MusicLibraryHttp`). The plugin holds
no music logic — it is just the adapter that turns HTTP endpoints into typed,
discoverable tools.

```
chat app -> OpenClaw Gateway -> agent
                                  | calls a registered tool
                                  v
                    music-library plugin (this package, TypeScript)
                                  | HTTP
                                  v
                    MusicLibraryHttp (Scala zio-http) -> Postgres + yt-dlp/ffmpeg
```

## 1. Run the HTTP server

The server lives in the `http` module of this repo and reuses the same
Postgres/fetcher wiring as the CLI.

```bash
# from the repo root
sbt "http/run"
```

It listens on `:8080` by default. Override with an env var:

```bash
MUSIC_LIBRARY_HTTP_PORT=9090 sbt "http/run"
```

Database connection settings are read from the existing
`main/src/main/resources/application.conf` (`music-library.postgres.*`), so the
usual `MUSIC_LIBRARY_POSTGRES_URL` / `_USER` / `_PASSWORD` env vars apply.

Quick check:

```bash
curl -s localhost:8080/health           # -> 200 OK
curl -s "localhost:8080/tracks?q=beatles" | jq .
```

### Endpoints

| Method | Path                              | Purpose                                        |
| ------ | --------------------------------- | ---------------------------------------------- |
| GET    | `/health`                         | Liveness check                                 |
| GET    | `/tracks`                         | Search: `?artist=&title=&status=&q=&page=&limit=` |
| GET    | `/tracks/{id}`                    | Get one track by UUID                          |
| POST   | `/tracks`                         | Create from URL: `{ "url": "..." }`            |
| PATCH  | `/tracks/{id}/{field}`            | Update a field (see below)                     |
| GET    | `/playlists`                      | Playlist name -> tracks                        |
| POST   | `/tracks/{id}/fetch`              | Start async fetch: `?force=&debug=`            |
| GET    | `/jobs/{id}`                      | Poll a fetch job                               |
| GET    | `/jobs`                           | List recent jobs                               |

Updatable fields: `artist`, `title`, `album`, `url`, `status`, `start-at`,
`end-at`. The body carries the value under a camelCase key, e.g.
`PATCH /tracks/{id}/start-at` with `{ "startAt": "00:00:10" }`. Send an empty
value to clear `start-at` / `end-at`.

Fetches are asynchronous: `POST /tracks/{id}/fetch` returns `202 Accepted` with
a `jobId`; poll `GET /jobs/{jobId}` until `state` is `succeeded` or `failed`.

## 2. Install the plugin in OpenClaw

During development, link this directory into your Gateway. Because it lives in a
workspace, OpenClaw treats it as a workspace-origin plugin (disabled by default),
so you must enable and allowlist it explicitly.

```bash
# link the local plugin directory
openclaw plugins install --link ./openclaw-plugin

# enable it
openclaw plugins enable music-library

# restart the Gateway so it loads the plugin code
openclaw gateway restart
```

Alternatively, point at it without installing by adding to `~/.openclaw/openclaw.json`:

```json5
{
  plugins: {
    load: { paths: ["/Users/mjaje/Desktop/prv-projects/offline-music-manager/openclaw-plugin"] },
    allow: ["music-library"],
    entries: {
      "music-library": {
        enabled: true,
        config: { baseUrl: "http://127.0.0.1:8080" },
      },
    },
  },
}
```

Verify the runtime actually loaded it:

```bash
openclaw plugins inspect music-library --runtime --json
```

## 3. Allowlist the mutating tools

Read tools (`music_search`, `music_get_track`, `music_list_playlists`,
`music_job_status`) are **required** and available once the plugin is enabled.

The side-effecting tools are **optional** and must be allowlisted before the
agent can call them:

```json5
{
  tools: {
    // allow the whole plugin...
    allow: ["music-library"],
    // ...or individual tools:
    // allow: ["music_add_track", "music_update_track", "music_fetch_track"],
  },
}
```

## Configuration

| Setting                    | Where                                             | Default                  |
| -------------------------- | ------------------------------------------------- | ------------------------ |
| API base URL               | `plugins.entries.music-library.config.baseUrl`    | `http://127.0.0.1:8080`  |
| API base URL (fallback)    | `MUSIC_LIBRARY_BASE_URL` env var                  | —                        |
| Server port                | `MUSIC_LIBRARY_HTTP_PORT` env var (Scala side)    | `8080`                   |

## Notes

- OpenClaw plugin APIs are experimental. The `openclaw` version fields in
  `package.json` are pinned to a beta baseline; bump them to match your
  installed Gateway and re-test on each version you declare compatible.
- For a portable install (another machine, or sharing), add a build step that
  emits `dist/index.js`, point `openclaw.extensions` at it, then publish to npm
  or ClawHub and install with `openclaw plugins install clawhub:<pkg>`.
