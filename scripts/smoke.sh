#!/usr/bin/env bash
# Happy-path smoke test for the transfer service.
#
# Usage: scripts/smoke.sh [service-url]
#   service-url  default http://localhost:8080
#   MOCK_URL     the mock bank, default http://localhost:9090
#
# Resets the mock bank and turns its random failures off first, so every run gives the same result.
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
MOCK_URL="${MOCK_URL:-http://localhost:9090}"
RUN="smoke-$(date +%s)-$$"
BODY="$(mktemp)"
trap 'rm -f "$BODY"' EXIT

QUIET='{"accounts": {"latencyMs": {"min": 0, "max": 0}, "errorRate": 0},
        "fx": {"latencyMs": {"min": 0, "max": 0}, "errorRate": 0},
        "fraud": {"latencyMs": {"min": 0, "max": 0}, "errorRate": 0, "slowRate": 0},
        "core": {"latencyMs": {"min": 0, "max": 0}, "hangRate": 0, "inquiryLatencyMs": {"min": 0, "max": 0}}}'

fail() { echo "FAIL  $*" >&2; exit 1; }
ok() { echo "ok    $*"; }

# The value of a string field in the JSON response, empty when absent or null.
field() { sed -n "s/.*\"$1\":\"\([^\"]*\)\".*/\1/p" "$BODY" | head -n 1; }

# transfer <idempotency-key> <source> <destination> <amount> <currency>
transfer() {
  STATUS=$(curl -sS -o "$BODY" -w '%{http_code}' --max-time 30 \
    -X POST "$BASE_URL/api/v1/transfers" \
    -H 'Content-Type: application/json' \
    -H "Idempotency-Key: $1" \
    -d "{\"sourceAccount\":\"$2\",\"destinationAccount\":\"$3\",\"amount\":\"$4\",\"currency\":\"$5\",\"description\":\"smoke test\"}")
}

curl -fsS -X POST "$MOCK_URL/__admin/reset" >/dev/null || fail "mock bank not reachable at $MOCK_URL"
curl -fsS -X PUT "$MOCK_URL/__admin/chaos" -H 'Content-Type: application/json' -d "$QUIET" >/dev/null
ok "mock bank reset, random failures off"

curl -sS --max-time 10 -o "$BODY" "$BASE_URL/actuator/health" || fail "service not reachable at $BASE_URL"
grep -q '"UP"' "$BODY" || fail "service not healthy: $(cat "$BODY")"
ok "health is UP"

transfer "$RUN-1" 2000000001 2000000002 25.00 USD
[ "$STATUS" = 201 ] || fail "same-currency transfer: HTTP $STATUS $(cat "$BODY")"
[ "$(field status)" = COMPLETED ] || fail "same-currency transfer: status '$(field status)'"
TRANSFER_ID=$(field transferId)
ok "same-currency transfer $TRANSFER_ID is COMPLETED"

transfer "$RUN-1" 2000000001 2000000002 25.00 USD
[ "$STATUS" = 201 ] || fail "replay: HTTP $STATUS $(cat "$BODY")"
[ "$(field transferId)" = "$TRANSFER_ID" ] || fail "replay returned another transfer: $(field transferId)"
ok "replay with the same Idempotency-Key returns the same transfer"

transfer "$RUN-2" 2000000001 1000000002 10.00 USD
[ "$STATUS" = 201 ] || fail "cross-currency transfer: HTTP $STATUS $(cat "$BODY")"
[ "$(field status)" = COMPLETED ] || fail "cross-currency transfer: status '$(field status)'"
grep -q '"credit":{"amount":"[0-9.]*","currency":"IDR"}' "$BODY" || fail "cross-currency transfer: no IDR credit in $(cat "$BODY")"
ok "cross-currency transfer is COMPLETED and credited in IDR"

STATUS=$(curl -sS -o "$BODY" -w '%{http_code}' --max-time 10 "$BASE_URL/api/v1/transfers/$TRANSFER_ID")
[ "$STATUS" = 200 ] || fail "read back: HTTP $STATUS $(cat "$BODY")"
[ "$(field status)" = COMPLETED ] || fail "read back: status '$(field status)'"
ok "transfer $TRANSFER_ID reads back as COMPLETED"

transfer "$RUN-3" 2000000001 2000000009 5.00 USD
[ "$STATUS" = 422 ] || fail "unknown destination: HTTP $STATUS $(cat "$BODY")"
[ "$(field reasonCode)" = ACCOUNT_NOT_FOUND ] || fail "unknown destination: reasonCode '$(field reasonCode)'"
ok "transfer to an unknown account is REJECTED with ACCOUNT_NOT_FOUND"

echo "Smoke test passed."
