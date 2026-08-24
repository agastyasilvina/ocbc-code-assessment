# Accounts API

Owned by the Accounts team. In development and in tests, the mock bank plays this service.

## GET /accounts/{accountNo}

```bash
curl http://localhost:9090/accounts/2000000001
```

```json
{"accountNo": "2000000001", "name": "ACME Customer 0001", "currency": "USD", "status": "ACTIVE", "available": "100000.00"}
```

| Field | Meaning |
|---|---|
| `currency` | `IDR`, `USD` or `SGD` |
| `status` | `ACTIVE`, `DORMANT` or `BLOCKED`. Only ACTIVE accounts can send or receive money. |
| `available` | Available balance, a decimal string |

| Status | Meaning |
|---|---|
| 200 | The account |
| 404 | No such account |
| 429 | Too many requests in flight. Wait for the number of seconds in `Retry-After`, then retry. |
| 503 | Temporary failure. Safe to retry. |

## Limits and behaviour

- **At most 20 requests in flight** per client. Beyond that: 429 with `Retry-After` in seconds.
- Usually answers within 150 ms; it can be slower.
- Transient 503s happen. Reads are safe to retry.

## Test accounts

Account numbers are deterministic:

- 10 digits. The first digit is the currency: `1` IDR, `2` USD, `3` SGD.
- The last digit decides the status: `7` DORMANT, `8` BLOCKED, `9` does not exist. Everything else is ACTIVE.
- Numbers ending in `11` have 10.00 available.
- Every other account has IDR 1,000,000,000.00, USD 100,000.00 or SGD 100,000.00.
- Balances never change.

| Example | Result |
|---|---|
| `2000000001` | USD, ACTIVE, 100,000.00 |
| `1000000002` | IDR, ACTIVE, 1,000,000,000.00 |
| `3000000011` | SGD, ACTIVE, 10.00 |
| `2000000007` | USD, DORMANT |
| `2000000008` | USD, BLOCKED |
| `2000000009` | not found (404) |
