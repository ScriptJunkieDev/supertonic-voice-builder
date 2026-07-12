#!/usr/bin/env bash
# Build eggs/supertonic/supertonic-server-bundle.zip from repo worker/ (+ trainer-backup README).
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
OUT="$HERE/supertonic-server-bundle.zip"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

mkdir -p "$TMP/worker" "$TMP/trainer-backup"
cp -a "$ROOT/worker/." "$TMP/worker/"
cp "$ROOT/trainer-backup/README.md" "$TMP/trainer-backup/"

rm -f "$OUT"
(cd "$TMP" && zip -rq "$OUT" worker trainer-backup)
echo "[build-bundle] $OUT ($(wc -c < "$OUT") bytes)"
