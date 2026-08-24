# How we build services

This is how our team builds backend services. There is no separate knowledge-transfer session: this guide, `docs/LEARNING.md` and the code in this repository are how you learn the stack.

The transfer service in this repository was left by a previous vendor. It does not always follow this guide.

## Reactive end to end

The transfer service must be reactive end to end: Spring WebFlux and Project Reactor all the way through. Every endpoint returns a `Mono` or a `Flux`, and no event-loop thread ever blocks. Blocking work runs on virtual threads, as described below.

**A service that is not reactive end to end is rejected, however well the rest of it works.** That includes a service built on Spring MVC, one that uses `CompletableFuture`, and one that blocks an event-loop thread.

## Shape of a service

- Java 21 and Spring Boot with Spring WebFlux. Versions are pinned in the root `pom.xml`. The runtime stays on Java 21.
- A Maven multi-module build, always run through the wrapper (`./mvnw`).
- Annotated controllers (`@RestController`) under versioned paths (`/api/v1/...`). A controller reads the request, calls one service method and maps the result. Business rules live in services, calls to other systems live in clients, and database access lives in repositories.
- Health and metrics through Spring Boot Actuator and Micrometer.
- Configuration comes from environment variables, each mapped to a property with a sensible default. Nothing environment-specific (hostnames, credentials, keys) is committed.

## Reactor at the edge

- `Mono` for one result, `Flux` for many results or a stream.
- A method that returns a `Mono` or a `Flux` builds a pipeline. Nothing runs until something subscribes. Keep side effects inside the pipeline.
- Never block an event-loop thread: no `block()`, no blocking I/O and no sleeping in code that runs there.
- Independent calls run in parallel (`Mono.zip`). Dependent calls chain (`flatMap`). Use `concatMap` only where order matters, and give `flatMap` a concurrency limit when the system you call has one.
- "Not found" is an error signal with a clear code (`switchIfEmpty(Mono.error(...))`), never a `null`.
- Fallbacks (`onErrorResume`, `defaultIfEmpty`) are for data that is nice to have. Anything that moves money or decides risk fails closed: an error stops the operation.

## Virtual threads for blocking work

- Some systems only offer blocking clients, such as vendor SDKs and legacy libraries. Those calls run on virtual threads, through a Reactor `Scheduler` backed by virtual threads, and the result flows back into the pipeline. The event loop never waits for them.
- Background jobs are plain imperative Java on virtual threads. They may block, on reactive repositories too, and they publish results back into Reactor through sinks. Several threads may emit into the same sink at once; make that safe.
- Limits, not pools. Virtual threads are cheap; the systems behind them are not. Limit concurrency with a semaphore sized to the documented limit of the system you call, shared by every code path that calls it. Never pool virtual threads.
- Pinning on Java 21. A virtual thread that blocks inside `synchronized` pins its carrier thread. With few CPUs, a handful of pinned threads stalls every other virtual thread. Measure with JFR (`jdk.VirtualThreadPinned`) before you trust throughput numbers.
- No `CompletableFuture`. Reactor at the edge, virtual threads behind it, and explicit hand-offs between the two.

## Data

- Postgres through Spring Data R2DBC. Repositories return `Mono` and `Flux`. When a derived query isn't enough, the SQL is written by hand in a `@Query` text block.
- Transactions are explicit (`TransactionalOperator`) and short. Never hold one open while calling another system.
- The schema is created by versioned migrations when the service starts.
- Money is `BigDecimal`, never `double`. Scale and rounding mode are always explicit.

## Integration

- A timeout on every call. Know the worst-case latency of your endpoint.
- Retry only what is safe to repeat, with backoff, and respect `Retry-After`.
- When a call times out after the request was sent, the outcome is unknown. Reconcile it; never send it again.
- Errors returned to clients are RFC 9457 problem details with a `code` and the correlation ID.
- Every request has a correlation ID (`X-Correlation-Id`, generated if absent). It is returned to the caller and appears on every log line for the request.

## Code style

- Java 21 features where they help: records for DTOs and query results, text blocks for SQL and JSON, `switch` expressions.
- Two-space indentation, four-space continuation lines.
- Lombok for logging (`@Slf4j`) and constructor injection (`@RequiredArgsConstructor`). Fields are `final`; no field injection.
- Logging through SLF4J with parameterised messages (`{}`), never string concatenation. When logging an error, pass the exception as the last argument.
- Comments explain why, not what.

## Testing

- JUnit 5 and Mockito for unit tests.
- Every method that returns a `Mono` or a `Flux` is tested with `StepVerifier`. Delays, timeouts and retries are tested in virtual time, so the test never waits.
- Virtual-thread and concurrent code is tested with latches and barriers, never with `Thread.sleep`.
- Integration tests run against real dependencies in containers (Testcontainers).
- Every incident gets a regression test that fails before the fix and passes after it.
- `./mvnw verify` fails below 80% line coverage (JaCoCo).

## Delivery

- Everything runs in Docker. `docker compose up --build` starts the whole system.
- Images are small: multi-stage builds, a JRE runtime image, a non-root user, memory settings that respect the container limit, and a graceful shutdown.
- CI (GitHub Actions) builds and tests every pull request.
- Feature branches, pull requests and Conventional Commits. Rebase a feature branch onto `main`; don't merge `main` into it. Platform fixes and backports come in with `git cherry-pick -x`. `docs/GIT_WORKFLOW.md` has the rules for this assessment.

## No AI tools

We don't use AI assistants, on work machines or in the browser.
