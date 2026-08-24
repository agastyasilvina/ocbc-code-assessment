#!/usr/bin/env bash
# INC-102: "During a core slowdown the service stopped answering everything, health checks
# included, and was restarted."
#
# A worked example of reproducing an incident with the mock bank's admin API.
# Every posting takes 3 seconds. We send a burst of transfers and time the health check while
# they are being posted. A health check that takes seconds means the threads that should serve
# requests are busy waiting.
#
# Usage: scripts/repro/INC-102.sh [service-url]
#   service-url  default http://localhost:8080
#   MOCK_URL     the mock bank, default http://localhost:9090
#   TRANSFERS    transfers in the burst, default 20
#
# Exit status: 0 when the incident is reproduced, 1 when it is not.
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
MOCK_URL="${MOCK_URL:-http://localhost:9090}"
TRANSFERS="${TRANSFERS:-20}"
RUN="inc102-$(date +%s)-$$"

SLOW_CORE='{"accounts": {"latencyMs": {"min": 0, "max": 0}, "errorRate": 0},
            "fx": {"latencyMs": {"min": 0, "max": 0}, "errorRate": 0},
            "fraud": {"latencyMs": {"min": 0, "max": 0}, "errorRate": 0, "slowRate": 0},
            "core": {"latencyMs": {"min": 3000, "max": 3000}, "hangRate": 0, "inquiryLatencyMs": {"min": 0, "max": 0}}}'

curl -fsS -X POST "$MOCK_URL/__admin/reset" >/dev/null
curl -fsS -X PUT "$MOCK_URL/__admin/chaos" -H 'Content-Type: application/json' -d "$SLOW_CORE" >/dev/null
echo "Mock bank: every posting takes 3 s."

echo "Sending $TRANSFERS transfers at once..."
PIDS=()
for i in $(seq 1 "$TRANSFERS"); do
  curl -s -o /dev/null --max-time 120 \
    -X POST "$BASE_URL/api/v1/transfers" \
    -H 'Content-Type: application/json' \
    -H "Idempotency-Key: $RUN-$i" \
    -d '{"sourceAccount":"2000000001","destinationAccount":"2000000002","amount":"1.00","currency":"USD"}' &
  PIDS+=($!)
done

sleep 1
SLOWEST=0
for check in 1 2 3 4 5; do
  SECONDS_TAKEN=$(curl -s -o /dev/null -w '%{time_total}' --max-time 30 "$BASE_URL/actuator/health" || echo 30)
  echo "Health check $check took ${SECONDS_TAKEN}s"
  SLOWEST=$(awk -v a="$SLOWEST" -v b="$SECONDS_TAKEN" 'BEGIN { print (b > a) ? b : a }')
  sleep 1
done

wait "${PIDS[@]}" || true
curl -fsS -X POST "$MOCK_URL/__admin/reset" >/dev/null

if awk -v s="$SLOWEST" 'BEGIN { exit !(s > 1.0) }'; then
  echo "Reproduced INC-102: a health check took ${SLOWEST}s while transfers were being posted."
  exit 0
fi
echo "Not reproduced: the slowest health check took ${SLOWEST}s."
exit 1
