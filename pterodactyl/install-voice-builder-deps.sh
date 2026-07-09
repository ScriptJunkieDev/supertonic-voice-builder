#!/usr/bin/env bash
# Runs on Pterodactyl install (Debian container). Files land in /mnt/server → /home/container.
# Installs PyTorch CPU stack into ./venv — NOT done by the Spring Boot app at runtime.
set -euo pipefail

cd /mnt/server
mkdir -p data data/tmp voices

REQ_URL="${TRAINER_PIP_REQUIREMENTS_URL:-https://raw.githubusercontent.com/ScriptJunkieDev/supertonic-voice-builder/main/worker/trainer-pip-requirements.txt}"
PY="${PYTHON_BIN:-python3}"

if ! command -v "$PY" >/dev/null 2>&1; then
  PY=python3
fi

export TMPDIR="/mnt/server/data/tmp"
export PIP_NO_CACHE_DIR=1

echo "Creating venv at /mnt/server/venv ..."
"$PY" -m venv venv

PIP="venv/bin/pip"
"$PIP" install --no-cache-dir --upgrade pip setuptools wheel

echo "Downloading pip requirements from $REQ_URL"
curl -fsSL -o trainer-pip-requirements.txt "$REQ_URL"

echo "Installing trainer Python dependencies (PyTorch CPU, etc.) — may take several minutes ..."
"$PIP" install --no-cache-dir -r trainer-pip-requirements.txt

cat > README.txt <<'EOF'
Supertonic Voice Builder (Java 21 + Python egg)

Install created:
  venv/                  — PyTorch CPU + trainer deps (egg install, not the Java app)
  data/                  — app uploads and jobs

Deploy:
  app.jar
  worker/

Panel env (defaults):
  PYTHON_BIN=./venv/bin/python3
  JAR_FILE=app.jar

The Java app clones supertonic3-voice-clone into ./supertonic3-voice-clone at startup only.
EOF

echo "Install complete. Upload app.jar + worker/, set PYTHON_BIN=./venv/bin/python3, start server."
