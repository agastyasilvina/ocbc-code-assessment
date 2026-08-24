# Mock bank

Stand-ins for the systems the transfer service integrates with: accounts, FX, fraud and core banking. It runs on port 9090, and `docker compose up` starts it.

It also has an admin API to inject failures (latency, errors, throttling, hangs) and to read what really happened, core postings included. Use it to reproduce incidents and in your integration tests.

Do not change this module: when we evaluate your work, we use our own copy of it.

- The services: `docs/integration/accounts-api.md`, `fx-api.md`, `fraud-api.md` and `core-banking-sdk.md`
- The admin API: `docs/integration/mock-bank-admin.md`
