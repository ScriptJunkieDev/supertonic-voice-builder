#!/usr/bin/env bash
# Supertonic Voice Builder — egg Reinstall (install image, root). /mnt/server = /home/container.
set -euo pipefail
cd /mnt/server

export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y --no-install-recommends ffmpeg libsndfile1 unzip || true

echo "[egg] Python on install image: $(python3 --version 2>&1)"
python3 -c 'import sys; (sys.version_info.major, sys.version_info.minor) >= (3, 10) or sys.exit("Need Python 3.10+ for torch 2.9")'

mkdir -p data data/tmp voices
export TMPDIR="/mnt/server/data/tmp"
export PIP_NO_CACHE_DIR=1

BUNDLE_URL="${SUPERTONIC_BUNDLE_URL:-https://raw.githubusercontent.com/ScriptJunkieDev/supertonic-voice-builder/main/eggs/supertonic/supertonic-server-bundle.zip}"

echo "[egg] Fetching app bundle from ${BUNDLE_URL}"
curl -fsSL -o supertonic-server-bundle.zip "$BUNDLE_URL"
unzip -oq supertonic-server-bundle.zip
rm -f supertonic-server-bundle.zip
chmod +x worker/*.py 2>/dev/null || true

if [[ ! -f worker/trainer-pip-requirements.txt ]]; then
  echo "[egg] ERROR: bundle missing worker/trainer-pip-requirements.txt" >&2
  exit 1
fi

echo "[egg] Creating ./venv ..."
python3 -m venv venv
PIP="venv/bin/pip"
"$PIP" install --no-cache-dir --upgrade pip setuptools wheel

echo "[egg] Installing PyTorch CPU + trainer deps from bundle (several minutes) ..."
"$PIP" install --no-cache-dir -r worker/trainer-pip-requirements.txt

cat > README.txt <<'EOF'
Supertonic Voice Builder (dedicated egg)

Install created:
  worker/   — from supertonic-server-bundle.zip (egg install)
  venv/     — PyTorch CPU + trainer deps (egg install)
  data/     — app uploads and jobs
  voices/   — trained voice JSON output

Upload: app.jar (Spring Boot fat JAR)

Panel: PYTHON_BIN=./venv/bin/python3  JAR_FILE=app.jar

Java app clones supertonic3-voice-clone at startup only (no pip).
EOF

echo "[egg] Done. venv python: $(venv/bin/python3 --version 2>&1)"
