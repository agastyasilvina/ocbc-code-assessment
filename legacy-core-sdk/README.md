# Core banking SDK

Blocking Java client for the ACME core banking system. Maintained by the Platform Team.

Version 1.0.0. Requires Java 21. Do not change this module in service repositories: fixes come from the Platform Team.

## Usage

```java
CoreBankingClient client = CoreBankingClient.create(CoreBankingConfig.fromEnv());

PostingResult result = client.post(new PostingRequest(
    reference, debitAccount, creditAccount,
    debitAmount, debitCurrency, creditAmount, creditCurrency));

Optional<PostingResult> found = client.inquire(reference);
```

Create one client per application and share it. The client is thread-safe.

## Configuration

| Environment variable | Required | Default | Meaning |
|---|---|---|---|
| `CORE_BASE_URL` | yes | | Base URL of the core, for example `http://core.example.com:9090` |
| `CORE_SDK_READ_TIMEOUT_MS` | no | `2000` | How long to wait for a response once a request has been sent |

The connect timeout is 500 ms and cannot be changed.

## Behaviour

- Every call opens a core session, performs one operation and closes the session. Session I/O is synchronized.
- The core allows at most 10 open sessions at a time. When none is free, the call fails with `CoreBusyException`.
- `post` is **not idempotent**. Every call creates a new posting, even when the core has already seen the reference.
- `inquire` is read-only and safe to retry. References are case-sensitive.
- The SDK never retries.

## Exceptions

All exceptions are unchecked and extend `CoreBankingException`.

| Exception | Meaning | What was sent |
|---|---|---|
| `CoreBusyException` | No session was available | Nothing. Safe to retry later. |
| `CoreUnavailableException` | The core could not be reached | Nothing. Safe to retry later. |
| `CoreTimeoutException` | The request was sent but no response arrived in time, or the connection was lost | **Unknown.** The posting may or may not exist: use `inquire`. |
| `CoreBankingException` | The core answered with an unexpected response | Depends on the operation |

## Changes

### 1.0.0

First release.
