#!/usr/bin/env bash
set -euo pipefail

# Imports the FunFriday metrics dashboard through Grafana's API. This avoids
# the V2-only JSON editor present in recent Grafana UI builds.

SCRIPT_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DASHBOARD_FILE="${SCRIPT_DIRECTORY}/funfriday-grafana-dashboard.json"
GRAFANA_URL="${GRAFANA_URL:-http://127.0.0.1:3001}"

if [[ ! -r "${DASHBOARD_FILE}" ]]; then
  echo "Dashboard file is missing: ${DASHBOARD_FILE}" >&2
  exit 1
fi

command -v curl >/dev/null || { echo "curl is required." >&2; exit 1; }
command -v jq >/dev/null || { echo "jq is required. Install it with: sudo apt install -y jq" >&2; exit 1; }

read -r -p "Grafana username [admin]: " GRAFANA_USERNAME
GRAFANA_USERNAME="${GRAFANA_USERNAME:-admin}"
read -r -s -p "Grafana password: " GRAFANA_PASSWORD
echo

TEMPORARY_RESPONSE="$(mktemp)"
trap 'rm -f "${TEMPORARY_RESPONSE}"' EXIT

# First use the current V1 resource API. It accepts the dashboard file exactly
# as it is stored in this repository.
STATUS_CODE="$(curl -sS -o "${TEMPORARY_RESPONSE}" -w '%{http_code}' \
  -u "${GRAFANA_USERNAME}:${GRAFANA_PASSWORD}" \
  -H 'Content-Type: application/json' \
  -X POST "${GRAFANA_URL}/apis/dashboard.grafana.app/v1/namespaces/default/dashboards" \
  --data-binary "@${DASHBOARD_FILE}")"

if [[ "${STATUS_CODE}" == "200" || "${STATUS_CODE}" == "201" ]]; then
  echo "FunFriday metrics dashboard imported successfully."
  exit 0
fi

# Some Grafana releases retain only the legacy dashboard import endpoint.
# It accepts the classic dashboard body found inside the V1 resource spec.
LEGACY_PAYLOAD="$(mktemp)"
trap 'rm -f "${TEMPORARY_RESPONSE}" "${LEGACY_PAYLOAD}"' EXIT
jq '{dashboard: (.spec | del(.__inputs)), overwrite: true}' "${DASHBOARD_FILE}" > "${LEGACY_PAYLOAD}"

STATUS_CODE="$(curl -sS -o "${TEMPORARY_RESPONSE}" -w '%{http_code}' \
  -u "${GRAFANA_USERNAME}:${GRAFANA_PASSWORD}" \
  -H 'Content-Type: application/json' \
  -X POST "${GRAFANA_URL}/api/dashboards/db" \
  --data-binary "@${LEGACY_PAYLOAD}")"

if [[ "${STATUS_CODE}" == "200" ]]; then
  echo "FunFriday metrics dashboard imported successfully through the legacy API."
  exit 0
fi

echo "Grafana dashboard import failed (HTTP ${STATUS_CODE}):" >&2
cat "${TEMPORARY_RESPONSE}" >&2
exit 1
