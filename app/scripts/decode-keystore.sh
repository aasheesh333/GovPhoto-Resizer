#!/usr/bin/env bash
# Writes release-keystore.jks (repo root) from KEYSTORE_BASE64 env var.
# Idempotent: skips if env var unset AND file already exists.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
KS="$ROOT/release-keystore.jks"

if [ -n "${KEYSTORE_BASE64:-}" ]; then
  printf '%s' "$KEYSTORE_BASE64" | base64 -d > "$KS"
  echo "Wrote $KS from KEYSTORE_BASE64"
elif [ -f "$KS" ]; then
  echo "release-keystore.jks already present"
else
  echo "WARNING: KEYSTORE_BASE64 unset and release-keystore.jks missing — release build will fall back to debug signing" >&2
fi
