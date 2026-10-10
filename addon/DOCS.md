# Offline Music Library — Home Assistant add-on

Runs the offline music library as a Home Assistant add-on: a headless downloader
with a small web UI and a JSON API. Finished tracks are written to a mapped HA
folder (e.g. under `/share`), and the API is reachable on the local network so an
[OpenClaw](https://github.com/openclaw/openclaw) agent can drive it.

## What it does

- Serves a web UI + JSON API on a fixed port (default `57381`).
- Writes fetched mp3s to a configurable folder under the mapped HA storage.
- Uses an **external** PostgreSQL database (e.g. another HA add-on).
- The same port is the API the `openclaw-plugin` in this repo talks to.

> Note: direct NFS mounting was removed and will return later with a different
> storage design. For now, point `output_dir` at a mapped HA folder (`/share` or
> `/media`); sync that folder to Navidrome by whatever means you already use.

## Requirements

- A reachable **PostgreSQL** server (host/port/db/user/password), for example the
  `core-postgres` add-on or any Postgres on your network.

## Installation

The add-on **pulls a prebuilt image** (published by CI) — the Supervisor does not
build anything on your HA box. `config.yaml` sets `image:` to the image repo and
`version:` to the tag to pull.

1. In Home Assistant: Settings → Add-ons → Add-on Store → ⋮ → **Repositories**,
   add the Git URL of this project.
2. Install **Offline Music Library**, configure it (below), then start. On
   install/update the Supervisor pulls the image matching your architecture
   (`amd64` or `aarch64`) at the `version` tag.

The image is HA-agnostic (a plain JRE image with `ffmpeg`, `yt-dlp`, and
`mutagen` bundled). The same image runs standalone with `docker run` — see the
project README.

## Network access

The add-on maps host port `57381` (configurable), so it is reachable at
`http://<HAOS-IP>:57381` from anywhere on your local network. That is the URL you
give the OpenClaw plugin (`baseUrl`). No ingress, no reverse proxy, no domain
needed — local network is enough.

## Configuration

```yaml
http_port: 57381
output_dir: "/share/offline-music-manager"
postgres_url: ""
postgres_host: "core-postgres"
postgres_port: 5432
postgres_database: "music_library"
postgres_user: "postgres"
postgres_password: "changeme"
cookies_file: ""
```

| Option              | Description                                                                                       |
| ------------------- | ------------------------------------------------------------------------------------------------- |
| `http_port`         | Host port for the UI + API. Reachable as `http://<HAOS-IP>:<port>`.                                |
| `output_dir`        | Folder where finished tracks are written. Point at a mapped HA path (`/share/...` or `/media/...`). |
| `postgres_url`      | Full JDBC URL. If set, it overrides the host/port/database fields.                                |
| `postgres_host`     | Postgres host (used when `postgres_url` is empty).                                                 |
| `postgres_port`     | Postgres port.                                                                                     |
| `postgres_database` | Database name.                                                                                     |
| `postgres_user`     | Database user.                                                                                     |
| `postgres_password` | Database password.                                                                                 |
| `cookies_file`      | Optional path to a yt-dlp cookies file (Netscape format) for restricted videos.                   |

### Postgres: string vs parts

Set **either** `postgres_url` (a full `jdbc:postgresql://host:port/db`) **or**
leave it empty and fill `postgres_host` / `postgres_port` / `postgres_database`.
`postgres_user` and `postgres_password` always apply.

## Using it from OpenClaw

Install the `openclaw-plugin` from this repo into your OpenClaw Gateway and set
its `baseUrl` to `http://<HAOS-IP>:<http_port>`. See `openclaw-plugin/README.md`.

## Notes

- The database schema is migrated automatically on start (Flyway).
- The cache directory lives on the add-on's `/data` volume to keep scratch I/O
  local.
