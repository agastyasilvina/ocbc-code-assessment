#!/usr/bin/env bash
# INC-101: "Customers were debited twice after core-banking timeouts."
#
# A worked example of reproducing an incident with the mock bank's admin API.
# The next postings hang for 5 seconds, and the core applies them anyway: the money moves while
# the service sees a timeout. Then we count how many postings the core received for one transfer.
# More than one means the customer was debited more than once.
#
# Usage: scripts/repro/INC-101.sh [service-url]
#   service-url  default http://localhost:8080
#   MOCK_URL     the mock bank, default http://localhost:9090
#
# Exit status: 0 when the incident is reproduced, 1 when it is not.
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
MOCK_URL="${MOCK_URL:-http://localhost:9090}"
BODY="$(mktemp)"
STATS="$(mktemp)"
trap 'rm -f "$BODY" "$STATS"' EXIT

QUIET='{"accounts": {"latencyMs": {"min": 0, "max": 0}, "errorRate": 0},
        "fx": {"latencyMs": {"min": 0, "max": 0}, "errorRate": 0},
        "fraud": {"latencyMs": {"min": 0, "max": 0}, "errorRate": 0, "slowRate": 0},
        "core": {"latencyMs": {"min": 0, "max": 0}, "hangRate": 0, "inquiryLatencyMs": {"min": 0, "max": 0}}}'

curl -fsS -X POST "$MOCK_URL/__admin/reset" >/dev/null
curl -fsS -X PUT "$MOCK_URL/__admin/chaos" -H 'Content-Type: application/json' -d "$QUIET" >/dev/null
curl -fsS -X PUT "$MOCK_URL/__admin/overrides" -H 'Content-Type: application/json' \
  -d '{"core": {"hangNext": 4, "applied": "all", "hangMs": 5000}}' >/dev/null
echo "Mock bank: the next 4 postings hang for 5 s, and the core applies them anyway."

KEY="inc101-$(date +%s)-$$"
echo "Sending one transfer with Idempotency-Key $KEY. This can take a while..."
STATUS=$(curl -sS -o "$BODY" -w '%{http_code}' --max-time 90 \
  -X POST "$BASE_URL/api/v1/transfers" \
  -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $KEY" \
  -d '{"sourceAccount":"2000000001","destinationAccount":"2000000002","amount":"42.00","currency":"USD"}')
echo "The service answered HTTP $STATUS: $(cat "$BODY")"

TRANSFER_ID=$(sed -n 's/.*"transferId":"\([^"]*\)".*/\1/p' "$BODY" | head -n 1)
curl -fsS -X DELETE "$MOCK_URL/__admin/overrides" >/dev/null
if [ -z "$TRANSFER_ID" ]; then
  echo "No transferId in the response, so the postings cannot be counted."
  exit 1
fi

# postings.byReference in the stats holds one JSON object per posting the core received.
curl -fsS "$MOCK_URL/__admin/stats" > "$STATS"
POSTINGS=$({ grep -o "\"$TRANSFER_ID\":\[[^]]*\]" "$STATS" || true; } | head -n 1 \
  | { grep -o '"coreTxnId"' || true; } | wc -l | tr -d ' ')
echo "Postings the core received for $TRANSFER_ID: $POSTINGS"

if [ "$POSTINGS" -gt 1 ]; then
  echo "Reproduced INC-101: the customer was debited $POSTINGS times."
  exit 0
fi
echo "Not reproduced."
exit 1
