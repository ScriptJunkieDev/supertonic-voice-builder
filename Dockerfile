# towncraft-ptero-arm - generic Java 21 + Python runtime for Pterodactyl (amd64 + arm64).
# No app-specific ENV, JAR, or pip installs.

FROM ghcr.io/pterodactyl/yolks:java_21 AS base

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
    && rm -rf /var/lib/apt/lists/*

FROM base AS runtime

USER container
WORKDIR /home/container

FROM runtime AS install

USER root
WORKDIR /mnt/server
