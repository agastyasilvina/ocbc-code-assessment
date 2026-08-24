# Web client

An operations console for the transfer service. It shows what the service does, especially when things fail.

Open http://localhost:8081 after `docker compose up --build`.

Do not change this module: when we evaluate your work, we use our own copy of it.

## Panels

- **New transfer**: sends `POST /api/v1/transfers` with a generated `Idempotency-Key`. *Send again (same key)* repeats the last request; *Send 5 at once (same key)* sends five identical requests in parallel.
- **Responses**: every response as the service returned it. Transfers show `status`, `reasonCode`, `transferId` and the correlation ID. Problem details (`application/problem+json`) show `title`, `code`, `detail` and `correlationId`. Anything else is shown in red as *Unrecognised error response*, with the HTTP status and the first 300 characters of the body. No answer within 10 seconds shows *No response from the service*.
- **Live feed**: subscribes to `GET /api/v1/transfers/events` and shows every status change.
- **Batch**: uploads an NDJSON file to `POST /api/v1/transfers/batch` and shows results as they stream in. *Stop* disconnects mid-batch.
- **Core ledger**: the mock bank's view: postings per reference, duplicates in red, hung postings with what really happened, core sessions, and a warning whenever the service's status disagrees with the core.
- **Chaos presets**: switch the mock bank between normal behaviour and common failure modes.

## Routes

nginx serves the console on port 8081 and proxies:

| Path | Goes to |
|---|---|
| `/api/` and `/actuator/health` | `transfer-service:8080` |
| `/mock/` | `mock-bank:9090`, without the `/mock` prefix |
