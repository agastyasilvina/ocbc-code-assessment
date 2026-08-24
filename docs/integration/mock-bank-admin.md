# Mock bank admin API

The mock bank (`mock-bank/`, port 9090) plays the accounts, FX, fraud and core banking systems. Its admin API lets you inject failures and read what really happened. Use it to reproduce incidents and in your integration tests.

The mock bank is shared by everything that talks to it. Reset it before each experiment.

## POST /__admin/reset

Clears all state and statistics, and restores the default chaos settings and no overrides.

```bash
curl -X POST http://localhost:9090/__admin/reset
```

## Chaos: probabilistic failures

`GET /__admin/chaos` shows the current settings. `PUT /__admin/chaos` changes them. Send only what you want to change; everything else keeps its value. Unknown fields and impossible values are refused with 400.

```bash
curl http://localhost:9090/__admin/chaos
```

The defaults, which are mild on purpose:

```json
{
  "seed": 42,
  "accounts": {"latencyMs": {"min": 20, "max": 150}, "errorRate": 0.03, "maxConcurrent": 20},
  "fx": {"latencyMs": {"min": 100, "max": 300}, "errorRate": 0.05, "maxPerSecond": 5, "validitySeconds": 30},
  "fraud": {"latencyMs": {"min": 50, "max": 250}, "errorRate": 0.02, "slowRate": 0.05, "slowMs": 2500},
  "core": {"latencyMs": {"min": 100, "max": 300}, "hangRate": 0.03, "hangMs": 5000, "applyOnHangRate": 0.5,
           "inquiryLatencyMs": {"min": 20, "max": 80}, "maxSessions": 10, "sessionTtlSeconds": 120}
}
```

| Setting | Meaning |
|---|---|
| `seed` | Seed of the random numbers behind every rate. The same seed gives the same sequence. |
| `latencyMs` | Each answer waits a random time between `min` and `max` milliseconds |
| `errorRate` | Share of calls that fail: 503 for accounts and fraud, 500 for FX |
| `accounts.maxConcurrent` | Calls in flight above this get 429 with `Retry-After` |
| `fx.maxPerSecond` | Calls per second above this get 429 with `Retry-After` |
| `fx.validitySeconds` | How long an issued rate is valid |
| `fraud.slowRate`, `fraud.slowMs` | Share of assessments that take `slowMs` |
| `core.hangRate`, `core.hangMs` | Share of postings whose answer takes `hangMs` |
| `core.applyOnHangRate` | Share of hung postings that the core applies anyway: the money moves, but the caller sees a timeout |
| `core.inquiryLatencyMs` | How long an inquiry takes |
| `core.maxSessions` | Open sessions allowed at once |
| `core.sessionTtlSeconds` | When a session that was never closed expires |

No latency and no failures, for deterministic tests:

```bash
curl -X PUT http://localhost:9090/__admin/chaos -H 'Content-Type: application/json' -d '{
  "accounts": {"latencyMs": {"min": 0, "max": 0}, "errorRate": 0},
  "fx": {"latencyMs": {"min": 0, "max": 0}, "errorRate": 0},
  "fraud": {"latencyMs": {"min": 0, "max": 0}, "errorRate": 0, "slowRate": 0},
  "core": {"latencyMs": {"min": 0, "max": 0}, "hangRate": 0, "inquiryLatencyMs": {"min": 0, "max": 0}}
}'
```

A slow core:

```bash
curl -X PUT http://localhost:9090/__admin/chaos -H 'Content-Type: application/json' \
  -d '{"core": {"latencyMs": {"min": 800, "max": 1200}}}'
```

## Overrides: deterministic scripts

`PUT /__admin/overrides` replaces all overrides at once. `GET /__admin/overrides` shows them and what is left of each script. `DELETE /__admin/overrides` removes them all.

```json
{
  "accounts": {
    "2000000001": {"responses": [{"status": 503}, {"status": 503}, {"status": 200}]},
    "2000000002": {"responses": [{"status": 429, "retryAfter": 1}]},
    "2000000003": {"then": {"status": 200, "latencyMs": 5000}},
    "2000000004": {"responses": [{"status": 200, "malformed": true}]}
  },
  "fx": {"fixedRates": {"USD/IDR": "16000.00000000"}, "failNext": 2, "failStatus": 500},
  "fraud": {"byAmount": {"7000.00": {"status": 503}, "8000.00": {"hangMs": 5000}, "9000.00": {"decision": "REVIEW"}}},
  "core": {"hangNext": 3, "applied": "alternate", "hangMs": 5000}
}
```

| Override | Meaning |
|---|---|
| `accounts.<no>.responses` | Answers for this account, one per call, in order. Status 200 means "answer normally". |
| `accounts.<no>.then` | The answer for every call once `responses` is used up |
| `retryAfter` | Seconds in the `Retry-After` header |
| `latencyMs` | Replaces the chaos latency for that answer |
| `malformed` | With status 200: a truncated body that is not valid JSON |
| `fx.fixedRates` | Always return this rate for the pair |
| `fx.failNext`, `fx.failStatus` | The next N rate calls fail with this status (add `retryAfter` for 429) |
| `fraud.byAmount.<amount>` | For this exact amount: wait `hangMs` first, or fail with `status`, or answer `decision` |
| `core.hangNext` | The next N postings hang for `hangMs` (default: the chaos `hangMs`) |
| `core.applied` | Which hung postings the core applies: `all`, `none` or `alternate` (first yes, second no, …). A hung posting that is not applied never exists; its caller gets 504 after the hang. |

Account 2000000001 answers 503 twice, then normally:

```bash
curl -X PUT http://localhost:9090/__admin/overrides -H 'Content-Type: application/json' \
  -d '{"accounts": {"2000000001": {"responses": [{"status": 503}, {"status": 503}, {"status": 200}]}}}'
```

The fraud service fails for every assessment of 7,000.00:

```bash
curl -X PUT http://localhost:9090/__admin/overrides -H 'Content-Type: application/json' \
  -d '{"fraud": {"byAmount": {"7000.00": {"status": 503}}}}'
```

The next posting hangs for 5 seconds, and the core applies it anyway:

```bash
curl -X PUT http://localhost:9090/__admin/overrides -H 'Content-Type: application/json' \
  -d '{"core": {"hangNext": 1, "applied": "all"}}'
```

```bash
curl http://localhost:9090/__admin/overrides
curl -X DELETE http://localhost:9090/__admin/overrides
```

## GET /__admin/stats

What each service saw, and what really happened in the core.

```bash
curl http://localhost:9090/__admin/stats
```

Every service reports:

| Field | Meaning |
|---|---|
| `calls` | Calls received |
| `inFlight`, `peakConcurrency` | Calls in progress now, and the most at once (refused calls included) |
| `statuses` | Calls per HTTP status |
| `callsByKey` | Call times (epoch milliseconds) per account number, currency pair (`USD/IDR`), transfer ID or core operation |

The FX section adds `ratesIssued` (pair, rate, `issuedAt`, `validUntil`). The fraud section adds `decisions`.

The core section adds:

| Field | Meaning |
|---|---|
| `sessions` | `open`, `opened`, `closed`, `peakOpen`, `busyRejections`, and `expiredWithoutClose` (sessions that were never closed) |
| `postings.byReference` | Every posting per reference: status, `coreTxnId`, accounts, amounts, `startedAt`, `endedAt`, `hung` |
| `duplicateReferences` | References posted more than once. Each extra posting moved money again. |
| `repeatedReferences` | References the core received more than once, whatever the result |
| `hung` | Hung postings, and whether the core applied them |
| `inquiries` | Inquiries per reference |

## The services

| Path | See |
|---|---|
| `GET /accounts/{accountNo}` | `accounts-api.md` |
| `GET /fx/rates` | `fx-api.md` |
| `POST /fraud/assessments` | `fraud-api.md` |
| `/core/…` | Only through the SDK: `core-banking-sdk.md` |
| `GET /health` | `{"status": "UP"}` |
