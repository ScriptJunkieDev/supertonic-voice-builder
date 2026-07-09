#!/usr/bin/env bash
# Same as egg-install.sh
set -euo pipefail
cd /mnt/server

echo "[egg] Python on install image: $(python3 --version 2>&1)"
python3 -c 'import sys; (sys.version_info.major, sys.version_info.minor) >= (3, 10) or sys.exit("Need Python 3.10+ for torch 2.9")'

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
