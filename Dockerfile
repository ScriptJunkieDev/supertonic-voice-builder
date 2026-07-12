# ghcr.io/<owner>/app:java21-python — Java 21 + Python for Pterodactyl (amd64 + arm64).
# No app-specific ENV, JAR, or pip installs.

FROM ghcr.io/pterodactyl/yolks:java_21

USER root

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        python3 \
        python3-pip \
        python3-venv \
        git \
        curl \
        ca-certificates \
        build-essential \
        ffmpeg \
        libsndfile1 \
        unzip \
    && rm -rf /var/lib/apt/lists/*

USER container
WORKDIR /home/container
