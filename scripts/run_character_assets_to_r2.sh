#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

if [[ ! -f .env ]]; then
  echo "Missing .env in repository root." >&2
  exit 1
fi

R2_ENV_FILE="${R2_ENV_FILE:-/home/ssm-user/r2-migration.env}"
if [[ ! -f "$R2_ENV_FILE" ]]; then
  echo "Missing R2 credential file: $R2_ENV_FILE" >&2
  echo "Create it with mode 600 before running --apply." >&2
  exit 1
fi

exec docker run --rm \
  --env-file .env \
  --env-file "$R2_ENV_FILE" \
  -v "$PWD/scripts:/work:ro" \
  python:3.12-slim \
  sh -lc 'pip install --quiet --disable-pip-version-check boto3==1.40.24 Pillow==11.3.0 PyMySQL==1.1.2 requests==2.32.5 && exec python /work/migrate_character_assets_to_r2.py "$@"' \
  migrate "$@"
