# syntax=docker/dockerfile:1
# ---------------------------------------------------------------------------
# HA-agnostic image for offline-music-manager.
# Build context is the repository root. Multi-stage: build with sbt, then a
# slim JRE runtime. No pre-staged jars committed to the repo.
# ---------------------------------------------------------------------------

# ---- Stage 1: build + stage the app with sbt ----
FROM sbtscala/scala-sbt:eclipse-temurin-21.0.12_8_1.13.0_3.3.8 AS builder
WORKDIR /src

# Dependency/build definition first for layer caching.
COPY build.sbt ./
COPY project ./project
RUN sbt -batch update || true

# Sources.
COPY domain ./domain
COPY http ./http

# Produce an unpacked, runnable app under http/target/universal/stage.
RUN sbt -batch "http/stage"

# ---- Stage 2: runtime ----
FROM eclipse-temurin:21-jre

# Runtime deps:
#  - ffmpeg        : audio conversion
#  - nfs-common    : mount the NFS music share (mount.nfs)
#  - python3-mutagen : provides the mid3v2 CLI for ID3 tagging
#  - jq            : entrypoint reads /data/options.json
#  - ca-certificates, curl : fetch the yt-dlp standalone binary
ARG YT_DLP_VERSION="2026.03.17"
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ffmpeg \
        nfs-common \
        python3-mutagen \
        jq \
        ca-certificates \
        curl \
    && curl -L -o /usr/local/bin/yt-dlp \
        "https://github.com/yt-dlp/yt-dlp/releases/download/${YT_DLP_VERSION}/yt-dlp" \
    && chmod a+x /usr/local/bin/yt-dlp \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# App + entrypoint.
COPY --from=builder /src/http/target/universal/stage /opt/music-library
COPY docker/entrypoint.sh /entrypoint.sh
RUN chmod a+x /entrypoint.sh /opt/music-library/bin/musiclibraryhttp

EXPOSE 8080

# entrypoint mounts NFS + ensures dirs, then execs the app in serve mode.
ENTRYPOINT [ "/entrypoint.sh" ]
CMD [ "/opt/music-library/bin/musiclibraryhttp", "serve" ]
