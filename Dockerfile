# syntax=docker/dockerfile:1
# ---------------------------------------------------------------------------
# HA-agnostic image for offline-music-manager.
# Build context is the repository root. Multi-stage: build with sbt, then a
# slim JRE runtime. No pre-staged jars committed to the repo.
# ---------------------------------------------------------------------------

# ---- Stage 1: build + stage the app with sbt ----
FROM sbtscala/scala-sbt:eclipse-temurin-21.0.12_8_1.13.0_3.3.8 AS builder
WORKDIR /src

# Cap the sbt/scalac JVM heap. Compiling ~9 sources needs little memory, but the
# JVM's ergonomic default heap (~25% of host RAM) can overshoot a tight build
# cgroup and trigger an OOM kill (exit 137). Keep the footprint small and
# deterministic regardless of the build host's RAM.
ENV SBT_OPTS="-Xmx2g -Xss32m -XX:MaxMetaspaceSize=512m"

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
#  - deno            : JS runtime required by yt-dlp-ejs for full YouTube support
#  - yt-dlp-ejs      : YouTube signature/challenge scripts (installed via pip)
ARG DENO_VERSION="v2.1.4"
# Bump/override CACHE_BUST (e.g. --build-arg CACHE_BUST=$(date +%s)) to force this
# layer to re-run and pull the current latest yt-dlp without a full --no-cache.
ARG CACHE_BUST=0
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ffmpeg \
        nfs-common \
        python3-mutagen \
        python3-pip \
        jq \
        ca-certificates \
        curl \
        unzip \
    # yt-dlp standalone binary — always the latest stable release. Each image
    # rebuild picks up the current version without changing a pinned tag
    # (YouTube breaks old yt-dlp often, so we want to move forward on rebuild).
    && curl -L -o /usr/local/bin/yt-dlp \
        "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp" \
    && chmod a+x /usr/local/bin/yt-dlp \
    # yt-dlp-ejs (YouTube JS challenge scripts). PEP 668: allow into system env.
    && pip3 install --no-cache-dir --break-system-packages yt-dlp-ejs \
    # Deno JS runtime (arch-aware), used by yt-dlp-ejs.
    && DENO_ARCH="$(dpkg --print-architecture)" \
    && case "$DENO_ARCH" in \
         amd64) DENO_TARGET="x86_64-unknown-linux-gnu" ;; \
         arm64) DENO_TARGET="aarch64-unknown-linux-gnu" ;; \
         *) echo "unsupported arch $DENO_ARCH" && exit 1 ;; \
       esac \
    && curl -fsSL -o /tmp/deno.zip \
        "https://github.com/denoland/deno/releases/download/${DENO_VERSION}/deno-${DENO_TARGET}.zip" \
    && unzip -o /tmp/deno.zip -d /usr/local/bin \
    && chmod a+x /usr/local/bin/deno \
    && rm -f /tmp/deno.zip \
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
