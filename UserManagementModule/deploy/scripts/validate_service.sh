#!/bin/bash
# ValidateService: wait until the API answers. The app has no health endpoint, so a GET on the
# signup path is used: a running app answers 405 (POST only). Anything else counts as not ready.
set -uo pipefail

set -a
# shellcheck disable=SC1091
. /etc/usermgmt/usermgmt.env
set +a
PORT="${PORT:-8080}"
URL="http://localhost:${PORT}/users/signup"

for attempt in $(seq 1 30); do
  if ! systemctl is-active --quiet usermgmt; then
    echo "usermgmt service is not active (attempt $attempt)"
    journalctl -u usermgmt -n 30 --no-pager || true
    exit 1
  fi
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "$URL" || true)
  if [ "$code" = "405" ]; then
    echo "Service is up (HTTP $code from $URL)"
    exit 0
  fi
  echo "Waiting for service (attempt $attempt, got '$code')"
  sleep 3
done

echo "Service did not become ready in time" >&2
journalctl -u usermgmt -n 50 --no-pager || true
exit 1
