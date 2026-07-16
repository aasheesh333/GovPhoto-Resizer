#!/usr/bin/env bash
# Populates secrets.properties (at repo root) from GitHub Actions secrets.
# Idempotent: skips any variable whose secret is unset (env var empty).
# Safe to run in local builds — falls back to template values.
# Does NOT touch gradle.properties (that file is tracked and holds build config).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SP="$ROOT/secrets.properties"
TEMPLATE="$ROOT/secrets.properties.template"

# Seed secrets.properties from template if absent
if [ ! -f "$SP" ]; then
  if [ ! -f "$TEMPLATE" ]; then
    echo "ERROR: neither secrets.properties nor secrets.properties.template found" >&2
    exit 1
  fi
  cp "$TEMPLATE" "$SP"
  echo "Created secrets.properties from template"
fi

# Helper: set or replace a key=value in secrets.properties
set_prop() {
  local key="$1"
  local val="$2"
  if grep -qE "^${key}=" "$SP"; then
    # Replace existing (portable sed: use temp file to avoid -i platform differences)
    sed -E "s|^${key}=.*|${key}=${val}|" "$SP" > "$SP.tmp" && mv "$SP.tmp" "$SP"
  else
    # Append
    echo "${key}=${val}" >> "$SP"
  fi
}

# Each variable: read from env (secret), if non-empty, write
[ -n "${PRIVACY_URL:-}" ]             && set_prop PRIVACY_URL "$PRIVACY_URL"
[ -n "${TERMS_URL:-}" ]              && set_prop TERMS_URL "$TERMS_URL"
[ -n "${CONTACT_URL:-}" ]            && set_prop CONTACT_URL "$CONTACT_URL"
[ -n "${ADMOB_APP_ID:-}" ]           && set_prop ADMOB_APP_ID "$ADMOB_APP_ID"
[ -n "${ADMOB_BANNER_UNIT:-}" ]      && set_prop ADMOB_BANNER_UNIT "$ADMOB_BANNER_UNIT"
[ -n "${ADMOB_INTERSTITIAL_UNIT:-}" ] && set_prop ADMOB_INTERSTITIAL_UNIT "$ADMOB_INTERSTITIAL_UNIT"
[ -n "${ADMOB_REWARDED_UNIT:-}" ]    && set_prop ADMOB_REWARDED_UNIT "$ADMOB_REWARDED_UNIT"
[ -n "${REVENUECAT_API_KEY:-}" ]     && set_prop REVENUECAT_API_KEY "$REVENUECAT_API_KEY"
[ -n "${ONESIGNAL_APP_ID:-}" ]       && set_prop ONESIGNAL_APP_ID "$ONESIGNAL_APP_ID"

echo "secrets.properties ready"
