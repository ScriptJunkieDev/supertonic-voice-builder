#!/bin/bash
# Supertonic Voice Builder — Pterodactyl egg install (supertonic-voice-builder-ptero:install, root).
set -euo pipefail
cd /mnt/server

export DEBIAN_FRONTEND=noninteractive

pick_python() {
  for ver in 12 11 10; do
    if command -v "python3.${ver}" >/dev/null 2>&1 \
      && "python3.${ver}" -c 'import sys; exit(0 if (3,10) <= sys.version_info[:2] < (3,13) else 1)'; then
      echo "python3.${ver}"
      return 0
    fi
  done
  apt-get update -qq
  for ver in 12 11 10; do
    if apt-get install -y --no-install-recommends "python3.${ver}" "python3.${ver}-venv" 2>/dev/null \
      && "python3.${ver}" -c 'import sys; exit(0 if (3,10) <= sys.version_info[:2] < (3,13) else 1)'; then
      echo "python3.${ver}"
      return 0
    fi
  done
  if python3 -c 'import sys; exit(0 if (3,10) <= sys.version_info[:2] < (3,13) else 1)'; then
    echo python3
    return 0
  fi
  echo "[egg] ERROR: need Python 3.10–3.12 (avoid 3.13+ for PyTorch/torchcodec stability)" >&2
  return 1
}

PY="$(pick_python)"
echo "[egg] Using ${PY} ($(${PY} --version 2>&1))"

mkdir -p data data/tmp voices
export TMPDIR="/mnt/server/data/tmp"
export PIP_NO_CACHE_DIR=1

REQ_URL="${TRAINER_PIP_REQUIREMENTS_URL:-https://raw.githubusercontent.com/ScriptJunkieDev/supertonic-voice-builder/main/worker/trainer-pip-requirements.txt}"

echo "[egg] Creating ./venv ..."
"${PY}" -m venv venv
PIP="venv/bin/pip"
"$PIP" install --no-cache-dir --upgrade pip setuptools wheel

echo "[egg] Fetching $REQ_URL"
curl -fsSL -o trainer-pip-requirements.txt "$REQ_URL"

echo "[egg] Installing PyTorch CPU + trainer deps (several minutes) ..."
"$PIP" install --no-cache-dir -r trainer-pip-requirements.txt
"$PIP" uninstall -y torchcodec 2>/dev/null || true

cat > README.txt <<'EOF'
Supertonic Voice Builder (Pterodactyl egg — not for other apps)

Install created:
  venv/     — PyTorch CPU + trainer deps (egg install)
  data/     — app uploads and jobs
  voices/   — trained voice JSON output

Deploy: app.jar + worker/

Panel: PYTHON_BIN=./venv/bin/python3  JAR_FILE=app.jar

Java app clones supertonic3-voice-clone at startup only (no pip).
EOF

echo "[egg] Done. venv python: $(venv/bin/python3 --version 2>&1)"
