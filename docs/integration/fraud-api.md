# Fraud API

Owned by the Risk team. In development and in tests, the mock bank plays this service.

## POST /fraud/assessments

```bash
curl -X POST http://localhost:9090/fraud/assessments \
  -H 'Content-Type: application/json' \
  -d '{"transferId": "8c1f0a52-6b1d-4d0e-9f4e-3c2b7a9d1e55", "amount": "7500.00", "currency": "USD", "sourceAccount": "2000000001", "destinationAccount": "1000000002"}'
```

```json
{"transferId": "8c1f0a52-6b1d-4d0e-9f4e-3c2b7a9d1e55", "decision": "ALLOW", "score": 0.08}
```

| `decision` | Meaning |
|---|---|
| `ALLOW` | The transfer may go ahead |
| `DENY` | Refuse the transfer |
| `REVIEW` | Hold the transfer for manual review |

| Status | Meaning |
|---|---|
| 200 | The decision |
| 400 | Missing `transferId` or `amount` |
| 503 | Temporary failure |

## Limits and behaviour

- **Idempotent per `transferId`**: asking again for the same transfer returns the same decision.
- Usually answers within 300 ms, but sometimes takes more than 2 seconds.
- **Clients must time out within 1.5 seconds.**
- Occasional 503s.

## Test hooks

| Amount ends in | Decision |
|---|---|
| `.66` | DENY |
| `.77` | REVIEW |
| anything else | ALLOW |
