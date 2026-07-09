#!/bin/bash
# Pterodactyl egg installation — use the SAME image as the running server (java21-python-yolk).
# Debian installers:debian only ships Python 3.9; PyTorch 2.9 needs 3.10+.
# /mnt/server is mounted as /home/container.
set -euo pipefail
cd /mnt/server

echo "[egg] Python on install image: $(python3 --version 2>&1)"
python3 -c 'import sys; v=sys.version_info; (v.major,v.minor) >= (3,10) or sys.exit("Need Python 3.10+ for torch 2.9 CPU wheels (rebuild/pull java21-python-yolk and reinstall server).")'

if ! command -v curl >/dev/null 2>&1; then
  export DEBIAN_FRONTEND=noninteractive
  echo "[egg] Installing curl ..."
  apt-get update -qq
  apt-get install -y --no-install-recommends curl ca-certificates
fi

mkdir -p data data/tmp voices
export TMPDIR="/mnt/server/data/tmp"
export PIP_NO_CACHE_DIR=1

REQ_URL="${TRAINER_PIP_REQUIREMENTS_URL:-https://raw.githubusercontent.com/ScriptJunkieDev/supertonic-voice-builder/main/worker/trainer-pip-requirements.txt}"

echo "[egg] Creating ./venv ..."
python3 -m venv venv
PIP="venv/bin/pip"
"$PIP" install --no-cache-dir --upgrade pip setuptools wheel

echo "[egg] Fetching $REQ_URL"
curl -fsSL -o trainer-pip-requirements.txt "$REQ_URL"

echo "[egg] Installing PyTorch CPU + trainer deps (several minutes) ..."
"$PIP" install --no-cache-dir -r trainer-pip-requirements.txt

cat > README.txt <<'EOF'
Supertonic Voice Builder (Java 21 + Python egg)

Install created:
  venv/     — PyTorch CPU + trainer deps (egg install)
  data/     — app uploads and jobs
  voices/   — trained voice JSON output

Deploy: app.jar + worker/

Panel: PYTHON_BIN=./venv/bin/python3  JAR_FILE=app.jar

Java app clones supertonic3-voice-clone at startup only (no pip).
EOF

echo "[egg] Done. venv python: $(venv/bin/python3 --version 2>&1)"
