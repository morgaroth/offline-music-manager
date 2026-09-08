# Offline Music Library — Home Assistant add-on

Runs the offline music library as a Home Assistant add-on: a headless downloader
with a small web UI and a JSON API. Finished tracks are written to an NFS share
(e.g. the same volume Navidrome serves), and the API is reachable on the local
network so an [OpenClaw](https://github.com/openclaw/openclaw) agent can drive it.

## What it does

- Serves a web UI + JSON API on a fixed port (default `57381`).
- Mounts your NFS music share inside the add-on and writes fetched mp3s there.
- Uses an **external** PostgreSQL database (e.g. another HA add-on).
- The same port is the API the `openclaw-plugin` in this repo talks to.

## Requirements

- A reachable **PostgreSQL** server (host/port/db/user/password), for example the
  `core-postgres` add-on or any Postgres on your network.
- An **NFS export** for the music library output (the Navidrome music volume).

## Installation

The add-on **pulls a prebuilt image** (published by CI) — the Supervisor does not
build anything on your HA box. `config.yaml` sets `image:` to the per-architecture
image repo and `version:` to the tag to pull.

1. In Home Assistant: Settings → Add-ons → Add-on Store → ⋮ → **Repositories**,
   add the Git URL of this project.
2. Install **Offline Music Library**, configure it (below), then start. On
   install/update the Supervisor pulls the image matching your architecture
   (`amd64` or `aarch64`) at the `version` tag.

The image is HA-agnostic (a plain JRE image with `ffmpeg`, `yt-dlp`, `nfs-common`,
and `mutagen` bundled). The same image runs standalone with `docker run` — see the
project README.

## Network access

The add-on maps host port `57381` (configurable), so it is reachable at
`http://<HAOS-IP>:57381` from anywhere on your local network. That is the URL you
give the OpenClaw plugin (`baseUrl`). No ingress, no reverse proxy, no domain
needed — local network is enough.

## Configuration

```yaml
http_port: 57381
nfs_enabled: true
nfs_export: "192.168.0.20:/volume1/music"
nfs_options: "rw,vers=4,proto=tcp,hard,timeo=600"
output_subdir: ""
postgres_url: ""
postgres_host: "core-postgres"
postgres_port: 5432
postgres_database: "music_library"
postgres_user: "postgres"
postgres_password: "changeme"
```

| Option              | Description                                                                                       |
| ------------------- | ------------------------------------------------------------------------------------------------- |
| `http_port`         | Host port for the UI + API. Reachable as `http://<HAOS-IP>:<port>`.                                |
| `nfs_enabled`       | Mount an NFS share for output. If `false`, set `MUSIC_LIBRARY_OUTPUT_DIR` or a default under the home dir is used. |
| `nfs_export`        | NFS export, `server:/export/path`. Required when `nfs_enabled` is true.                            |
| `nfs_options`       | `mount -o` options for the NFS mount.                                                              |
| `output_subdir`     | Optional subfolder under the NFS mount. Leave empty to write directly into the mounted music dir (`/mnt/music`); set it to nest tracks in `/mnt/music/<output_subdir>`. |
| `postgres_url`      | Full JDBC URL. If set, it overrides the host/port/database fields.                                |
| `postgres_host`     | Postgres host (used when `postgres_url` is empty).                                                 |
| `postgres_port`     | Postgres port.                                                                                     |
| `postgres_database` | Database name.                                                                                     |
| `postgres_user`     | Database user.                                                                                     |
| `postgres_password` | Database password.                                                                                 |

### Postgres: string vs parts

Set **either** `postgres_url` (a full `jdbc:postgresql://host:port/db`) **or**
leave it empty and fill `postgres_host` / `postgres_port` / `postgres_database`.
`postgres_user` and `postgres_password` always apply.

### NFS output for Navidrome

Point `nfs_export` at the same export Navidrome reads, and set `output_subdir` to
the folder you want the tracks under. On start the add-on mounts the export at
`/mnt/music` inside the container and writes to `/mnt/music/<output_subdir>`, so
Navidrome sees new files directly.

NFS mounting needs elevated container privileges; this add-on already requests
`SYS_ADMIN` + `DAC_READ_SEARCH` and disables AppArmor for that reason.

## Using it from OpenClaw

Install the `openclaw-plugin` from this repo into your OpenClaw Gateway and set
its `baseUrl` to `http://<HAOS-IP>:<http_port>`. See `openclaw-plugin/README.md`.

## Notes

- The database schema is migrated automatically on start (Flyway).
- The cache directory lives on the add-on's `/data` volume, not on the NFS share,
  to keep scratch I/O off the network disk.
