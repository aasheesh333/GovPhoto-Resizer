#!/usr/bin/env bash
# Writes app/google-services.json from GOOGLE_SERVICES_JSON_BASE64 env var.
# Idempotent: skips if env var unset AND file already exists.
set -euo pipefail

APP_DIR="$(cd "$(dirname "$0")/.." && pwd)"
GS="$APP_DIR/google-services.json"

if [ -n "${GOOGLE_SERVICES_JSON_BASE64:-}" ]; then
  printf '%s' "$GOOGLE_SERVICES_JSON_BASE64" | base64 -d > "$GS"
  echo "Wrote $GS from GOOGLE_SERVICES_JSON_BASE64"
elif [ -f "$GS" ]; then
  echo "google-services.json already present"
else
  echo "WARNING: GOOGLE_SERVICES_JSON_BASE64 unset and google-services.json missing — Firebase init will fail" >&2
fi
