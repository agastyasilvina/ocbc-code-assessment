# FX API

Owned by the Treasury team. In development and in tests, the mock bank plays this service.

## GET /fx/rates?base={currency}&quote={currency}

```bash
curl "http://localhost:9090/fx/rates?base=USD&quote=IDR"
```

```json
{"base": "USD", "quote": "IDR", "rate": "16250.5037", "validUntil": "2026-03-02T02:16:00.123Z"}
```

| Field | Meaning |
|---|---|
| `rate` | Units of `quote` for one unit of `base`. A decimal string with at most 8 decimal places. |
| `validUntil` | The rate may be used until this instant: 30 seconds after it was issued. Never use a rate after it expires. |

| Status | Meaning |
|---|---|
| 200 | The rate |
| 400 | Unsupported pair: base and quote must be two different currencies out of IDR, USD and SGD |
| 429 | Rate limit exceeded. Wait for the number of seconds in `Retry-After`, then retry. |
| 500 | Temporary failure. Safe to retry. |

## Limits and behaviour

- **At most 5 requests per second** per client. Beyond that: 429 with `Retry-After` in seconds.
- Usually answers within 300 ms.
- Transient 500s happen.
- A rate can be reused for as long as it is valid.

## Rates in the mock bank

| Pair | Rate |
|---|---|
| USD → IDR | 16250.5037 |
| SGD → IDR | 12100.2519 |
| USD → SGD | 1.3431 |

The inverse pairs are 1 divided by these, rounded to 8 decimal places: IDR → USD is `0.00006154`.
