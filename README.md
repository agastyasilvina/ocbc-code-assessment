# ACME Bank transfer service

> **Before you start**
>
> - **Fork this repository and do all your work in your own fork.** Your fork is your submission. `docs/GIT_WORKFLOW.md` has the rules.
> - **Your whole solution must be Java, reactive end to end with Project Reactor:** Spring WebFlux, with `Mono` and `Flux` from every endpoint through every integration. Blocking work runs on virtual threads, bridged to Reactor. A solution that is not reactive end to end is rejected, however well the rest of it works.

## Scenario

You are joining ACME Bank's payments integration team. A previous vendor left this transfer service behind, and operations has a list of incidents. The service handles the happy path. It fails when the systems around it fail.

These two weeks are how you learn our stack. There is no separate knowledge-transfer session: this repository, its documentation and its code are what you learn from.

## Your job

1. **Fix the incidents** below. Every fix comes with an automated regression test that reproduces the incident.
2. **Build the three features** below.
3. **Do the Git tasks**: bring in the two SDK fixes, deal with SEC-301, cut the release and ship the INC-112 hotfix. `docs/GIT_WORKFLOW.md` has the rules.
4. **Run everything in Docker, with CI** (see *Docker and CI*).
5. **Explain your work** in `DESIGN.md` at the root of your fork: copy `docs/DESIGN_TEMPLATE.md` and answer every question.

### Incidents

| ID | Reported by operations |
|---|---|
| INC-101 | Customers were debited twice after core-banking timeouts. |
| INC-102 | During a core slowdown the service stopped answering everything, health checks included, and was restarted. |
| INC-103 | The FX provider throttled us (HTTP 429) during a promotion; cross-currency transfers failed. |
| INC-104 | A large transfer went through while the fraud service was down. |
| INC-105 | A mobile-app retry created two transfers. |
| INC-106 | Some cross-currency credit amounts are off by 0.01. |
| INC-107 | The container is OOMKilled in UAT (2 CPUs / 512 MB). |
| INC-108 | The reconciliation job floods core banking with "session busy" errors. |
| INC-109 | Transfers were reported FAILED although the money had moved. |
| INC-110 | The service cannot reach its dependencies when started with docker compose. |
| INC-111 | The web client shows "Unrecognised error response" for many failures, sometimes with a stack trace, and support can't find the request in the logs. |
| SEC-301 | A credential was committed to the repository. |
| SDK-101, SDK-107 | Fixed by the platform team on `platform/sdk-next`. Bring in only these two fixes. |
| INC-112 | Arrives after release 1.0: see `docs/GIT_WORKFLOW.md`. |

We know of more problems than these. Finding and fixing them counts.

`scripts/repro/INC-101.sh` and `scripts/repro/INC-102.sh` show how to reproduce an incident with the mock bank's admin API. Reproduce the others as automated tests.

### Features

| ID | Feature |
|---|---|
| FEAT-201 | Batch transfers with streamed results: `POST /api/v1/transfers/batch` |
| FEAT-202 | Reconciliation on virtual threads, replacing the current reconciliation job: `POST /internal/reconciliation/run`, and a scheduled run |
| FEAT-203 | Live transfer events as server-sent events, for the web client's live feed: `GET /api/v1/transfers/events` |

`docs/api.md` is the contract for the API and all three features.

### Docker and CI

- `docker compose up --build` from a clean clone starts `postgres`, `mock-bank`, `transfer-service` and `web-client`, all healthy, and the web client reaches the API through its proxy.
- Keep those four service names. Don't change the `mock-bank` and `web-client` definitions in `docker-compose.yml`: we replace them with our own copies.
- `transfer-service` gets 2 CPUs and 512 MB, a healthcheck, `depends_on` with `condition: service_healthy`, and all its configuration through environment variables.
- The Dockerfile is multi-stage, runs a Java 21 JRE image as a non-root user, and has no build tools or sources in the final image. It comes with a `.dockerignore`, memory settings that respect the container limit, and a graceful shutdown.
- GitHub Actions in your fork run `./mvnw verify` and build the image on every pull request. Your final pull requests are green.
- At least one integration test runs against the real `mock-bank` container (Testcontainers).

## Rules

- The runtime is Java 21.
- Everything runs in Docker.
- **Reactive end to end.** The API uses Spring WebFlux with Reactor `Mono` and `Flux`. Blocking integrations and background work run on virtual threads, bridged to Reactor as `docs/STACK.md` describes. No `CompletableFuture`. **A service that is not reactive end to end is rejected, however well the rest of it works.** That includes a service built on Spring MVC, one that uses `CompletableFuture`, and one that blocks an event-loop thread.
- Unit tests use JUnit 5 and `StepVerifier`. `./mvnw verify` fails below 80% line coverage.
- The API follows the contract in `docs/api.md`.
- All configuration comes from environment variables.
- Don't edit `mock-bank/` or `web-client/`. Change `legacy-core-sdk/` only through the two cherry-picks.

## How we evaluate

We run your service under failure injection (latency, errors, throttling, timeouts) inside 2 CPUs and 512 MB, and check the correctness rules a bank depends on. We read your code, your tests and their coverage, your Git history and pull requests, and your `DESIGN.md`.

*An incomplete but correct solution beats a complete but unsafe one.*

## Timeline

Two weeks. Your vendor manager gives you the exact dates. There is one deadline, at the end of the two weeks.

## Going further

- Idempotency that holds across 3 replicas, and reconciliation with row locking (`FOR UPDATE SKIP LOCKED`), so two instances never process the same transfer.
- BlockHound in your tests. `StepVerifier.withVirtualTime` for retries and backoff.
- The correlation ID carried through the Reactor context and into the reconciliation worker's logs.
- A Resilience4j circuit breaker or bulkhead, with metrics.
- PIT mutation testing, with the mutation score reported.

## Learning

Start with `docs/STACK.md`, then `docs/LEARNING.md` and the `platform/test-kit` branch. Nobody is expected to know everything on day one.

## Working without AI

> **Working without AI.** When you work with us, you won't have access to AI assistants on your machine or in your browser. Use these two weeks to learn our stack the way you'll work with it: from docs, source code and tests. Don't use AI tools to write your solution. All submissions are compared automatically with each other and with solutions generated by common AI coding tools from this repository, and shortlisted candidates extend their own solution live, without AI. If you did use an AI tool, say where in `DESIGN.md`.

## Submission

By the deadline, send your vendor manager your fork URL, the SHA of your `main` and the SHA of `v1.0.1`. The vendor manager forwards them to us unchanged. Your fork is public, so we can read it without you giving us access. Commits after the deadline are ignored.

Questions go to your vendor manager too. Answers appear in `docs/FAQ.md` on this repository's `main`, so everyone gets the same answer.

## Getting started

Fork this repository on GitHub and clone your fork. `docs/GIT_WORKFLOW.md` has the rules for your fork, branches and pull requests.

You need Docker with Docker Compose, and JDK 21 to build and run tests.

```bash
docker compose up --build
```

- The web client is at http://localhost:8081. Its chaos presets switch the mock bank between normal behaviour and common failures.
- The mock bank is at http://localhost:9090. `docs/integration/mock-bank-admin.md` describes its admin API.

As the code stands, the transfer service can't reach its dependencies inside compose (INC-110). Until you fix that, run it on your machine against the containers:

```bash
docker compose up -d postgres mock-bank
./mvnw -DskipTests install
./mvnw -pl transfer-service spring-boot:run
```

Then, in another terminal:

```bash
scripts/smoke.sh
```

`scripts/smoke.sh` runs the happy path against http://localhost:8080 (pass another URL as its argument). It passes on the code as it stands.

## Repository

| Path | What it is |
|---|---|
| `docs/STACK.md` | How we build services. Read it first. |
| `docs/api.md` | The agreed API contract |
| `docs/integration/` | The systems you integrate with, and the mock bank's admin API |
| `docs/LEARNING.md` | Reading list |
| `docs/GIT_WORKFLOW.md` | Fork, branches, cherry-picks, release |
| `docs/DESIGN_TEMPLATE.md` | Copy to `DESIGN.md` and answer |
| `docs/FAQ.md` | Answers to candidates' questions |
| `transfer-service/` | The service you fix and extend |
| `legacy-core-sdk/` | Core banking SDK (don't edit, except the two cherry-picks) |
| `mock-bank/` | Accounts, FX, fraud and core stand-ins (don't edit) |
| `web-client/` | Operations console (don't edit) |
| `scripts/` | Smoke test and incident reproductions |
