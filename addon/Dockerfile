# https://developers.home-assistant.io/docs/add-ons/configuration#add-on-dockerfile
ARG BUILD_FROM=ghcr.io/hassio-addons/ubuntu-base:9.0.0
FROM ${BUILD_FROM}

# Runtime deps (Ubuntu 24.04 LTS via the HA Ubuntu base image):
#  - openjdk-21-jre-headless : run the app
#  - nfs-common              : mount the NFS music share (mount.nfs)
#  - ffmpeg                  : audio conversion (used by ZIOFetcher)
#  - python3-mutagen         : provides the mid3v2 CLI for ID3 tagging
#  - ca-certificates + curl  : fetch the yt-dlp standalone binary
RUN \
    apt-get update \
    && apt-get install -y --no-install-recommends \
        openjdk-21-jre-headless \
        nfs-common \
        ffmpeg \
        python3-mutagen \
        ca-certificates \
        curl \
    \
    # yt-dlp: install the official standalone binary rather than the apt package,
    # which lags far behind and breaks against YouTube. Pinning avoids surprise
    # changes; bump YT_DLP_VERSION to update.
    && YT_DLP_VERSION="2026.03.17" \
    && curl -L -o /usr/local/bin/yt-dlp \
        "https://github.com/yt-dlp/yt-dlp/releases/download/${YT_DLP_VERSION}/yt-dlp" \
    && chmod a+x /usr/local/bin/yt-dlp \
    \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# The pre-staged app is produced by ./stage-addon.sh (sbt http/stage) and lands
# in rootfs/opt/music-library, so it is inside this add-on's build context.
# Copy the add-on root filesystem (run.sh + staged app).
COPY rootfs /

RUN chmod a+x /run.sh \
    && chmod a+x /opt/music-library/bin/musiclibraryhttp

# The Ubuntu base sets ENTRYPOINT ["/init"] (s6). With `init: false` in
# config.yaml we bypass s6 and run our script directly.
ENTRYPOINT []
CMD [ "/run.sh" ]

LABEL \
    org.opencontainers.image.title="Offline Music Library" \
    org.opencontainers.image.source="https://gitlab.com/mateuszjajedev/offline-music-manager"
