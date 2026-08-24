# Core banking SDK

The core banking system is reached only through the Platform Team's SDK in `legacy-core-sdk/`. Its `README.md` is the vendor documentation; this page adds what matters when you integrate with it.

## Usage

```java
CoreBankingClient client = CoreBankingClient.create(new CoreBankingConfig(baseUrl, readTimeoutMs));
// or CoreBankingClient.create(CoreBankingConfig.fromEnv()):
//   CORE_BASE_URL (required), CORE_SDK_READ_TIMEOUT_MS (default 2000); connect timeout 500 ms

PostingResult post(PostingRequest request);        // NOT idempotent
Optional<PostingResult> inquire(String reference); // read-only, safe to retry
```

```java
record PostingRequest(String reference, String debitAccount, String creditAccount,
                      BigDecimal debitAmount, String debitCurrency,
                      BigDecimal creditAmount, String creditCurrency) {}

record PostingResult(String reference, String coreTxnId, Status status /* POSTED, REJECTED */,
                     String reasonCode, Instant postedAt) {}
```

## Behaviour

- **Blocking.** Every call opens a core session, performs one operation and closes the session. Session I/O is synchronized.
- **At most 10 open sessions** at a time, for all callers together. When none is free, the call fails with `CoreBusyException`. A session that is never closed expires after 120 seconds.
- **`post` is not idempotent.** The core never deduplicates: posting the same reference twice moves the money twice.
- **`inquire` is read-only** and safe to retry. References are case-sensitive.
- The core rejects postings for accounts that do not exist or are not active, debits above the available balance, and currencies that do not match the accounts. The result is `REJECTED` with a `reasonCode`.
- The client is thread-safe. The SDK never retries.

## Exceptions

| Exception | Meaning | What reached the core |
|---|---|---|
| `CoreBusyException` | No session available | Nothing. Safe to retry later. |
| `CoreUnavailableException` | The core could not be reached | Nothing. Safe to retry later. |
| `CoreTimeoutException` | The request was sent but no answer arrived in time, or the connection was lost | **Unknown.** The posting may or may not exist: use `inquire`. |
| `CoreBankingException` | The core answered with something unexpected | Depends on the call |

## Branches

The Platform Team develops the SDK on `platform/sdk-next`. That branch holds fixes and work in progress; see `docs/GIT_WORKFLOW.md` for what to take from it.
