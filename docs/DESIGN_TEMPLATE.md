# Design

Copy this file to `DESIGN.md` at the root of your fork and answer every question. Refer to your own code (file paths) and your own runs. Short and specific beats long and general.

## 1. Problems found

Every problem you found in the original code, including ones that are not in the incident list. For each: the file, the cause, your fix, and the test that proves it.

## 2. Threads

Which code runs on which threads (event loop, schedulers, virtual threads), and where blocking happens.

## 3. Trace

One transfer that became PENDING in your own run: its log lines, with the correlation ID, up to its final status.

## 4. Core SDK

Where the SDK calls run, what limits concurrency across the API path and reconciliation, and what happens when 200 requests arrive at once.

## 5. Timeouts and retries

A table per dependency: timeout, retries, backoff, which statuses are retried, and why. Then the worst-case latency of one transfer, calculated.

## 6. Fraud unavailable

What you chose, and why.

## 7. Idempotency

How one posting is guaranteed under concurrent duplicates, what happens after a restart, and what changes with 3 replicas.

## 8. Virtual threads

Why this design. How Reactor and virtual threads hand work to each other in your code. What the SDK's synchronized session I/O means on Java 21, and how you measured it (numbers).

## 9. Streams

- Batch: concurrency, per-account ordering, slow clients, disconnects.
- Events: how events from event-loop threads and from virtual threads reach subscribers safely, what a slow subscriber does, and what happens on disconnect.

## 10. Docker

Base image, image size, user, memory settings, and what happens on `docker stop` in the middle of a batch (measured).

## 11. Git and security

What conflicted in SDK-107 and how you resolved it. SEC-301: what you did in the repository, and what must happen outside it.

## 12. Production

What breaks at 3 replicas or at 10 times the traffic, and what you would do with another week.

## 13. Learning

What you didn't know at the start, and how you learned it.

## 14. AI

Did you use any AI tool? If so, where and for what.

## 15. Testing

What you test at which level. How you test `Mono`, `Flux` and virtual-thread code without sleeping. What your coverage misses.
