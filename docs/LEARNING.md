# Learning

Where to learn what this assessment needs. Official sources first. There are no solutions here: the point is to learn the tools, then apply them.

Start with `docs/STACK.md`, then pick what you need below. The `platform/test-kit` branch has worked test examples you can cherry-pick.

## Reactor and WebFlux

- [Reactor reference guide](https://projectreactor.io/docs/core/release/reference/): read it once, end to end.
  - [Which operator do I need?](https://projectreactor.io/docs/core/release/reference/apdx-operatorChoice.html)
  - [Threading and schedulers](https://projectreactor.io/docs/core/release/reference/coreFeatures/schedulers.html)
  - [Handling errors](https://projectreactor.io/docs/core/release/reference/coreFeatures/error-handling.html), including retries
  - [Sinks](https://projectreactor.io/docs/core/release/reference/coreFeatures/sinks.html), for publishing into Reactor safely from any thread
  - [Hot versus cold](https://projectreactor.io/docs/core/release/reference/advancedFeatures/reactor-hotCold.html)
  - [Context](https://projectreactor.io/docs/core/release/reference/advancedFeatures/context.html)
  - [Testing](https://projectreactor.io/docs/core/release/reference/testing.html): `StepVerifier`, virtual time, `TestPublisher`
- Javadoc, read the operator descriptions and marble diagrams:
  - [`Flux`](https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html): `flatMap` and its concurrency argument, `concatMap`, `groupBy`
  - [`Schedulers`](https://projectreactor.io/docs/core/release/api/reactor/core/scheduler/Schedulers.html): bounded-elastic caps, and schedulers backed by virtual threads
  - [`Sinks`](https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Sinks.html)
- [Spring WebFlux reference](https://docs.spring.io/spring-framework/reference/web/webflux.html)
  - [Reactive core, codecs and streaming](https://docs.spring.io/spring-framework/reference/web/webflux/reactive-spring.html): NDJSON and server-sent events
  - [WebClient](https://docs.spring.io/spring-framework/reference/web/webflux-webclient.html)
  - [Error responses (RFC 9457)](https://docs.spring.io/spring-framework/reference/web/webflux/ann-rest-exceptions.html)
  - [`ResponseEntity`](https://docs.spring.io/spring-framework/reference/web/webflux/controller/ann-methods/responseentity.html)
- [Spring Data R2DBC](https://docs.spring.io/spring-data/relational/reference/r2dbc.html)
- [Spring Boot: SQL databases, R2DBC and migrations](https://docs.spring.io/spring-boot/reference/data/sql.html)

## Virtual threads

- [JEP 444: Virtual Threads](https://openjdk.org/jeps/444). Read the section on pinning twice.
- [JEP 491: Synchronize Virtual Threads without Pinning](https://openjdk.org/jeps/491): what changed after Java 21, and why it does not apply here.
- [Oracle: Virtual threads](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html), including the JFR event `jdk.VirtualThreadPinned`.
- Reactor schedulers backed by virtual threads: see the [`Schedulers`](https://projectreactor.io/docs/core/release/api/reactor/core/scheduler/Schedulers.html) javadoc.

## Integration

- [RFC 9457: Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc9457)
- [The Idempotency-Key HTTP header (IETF draft)](https://datatracker.ietf.org/doc/draft-ietf-httpapi-idempotency-key-header/)
- [RFC 9110: `Retry-After`](https://www.rfc-editor.org/rfc/rfc9110#name-retry-after)
- [Resilience4j documentation](https://resilience4j.readme.io/docs)

## Docker

- [Multi-stage builds](https://docs.docker.com/build/building/multi-stage/)
- [Compose: control startup order with healthchecks](https://docs.docker.com/compose/how-tos/startup-order/)
- [Resource constraints](https://docs.docker.com/engine/containers/resource_constraints/) and [Compose `deploy.resources`](https://docs.docker.com/reference/compose-file/deploy/)
- [The `java` command](https://docs.oracle.com/en/java/javase/21/docs/specs/man/java.html): `-XX:MaxRAMPercentage` and how the JVM sizes itself in a container
- [Spring Boot: Dockerfiles](https://docs.spring.io/spring-boot/reference/packaging/container-images/dockerfiles.html)
- [Spring Boot: graceful shutdown](https://docs.spring.io/spring-boot/reference/web/graceful-shutdown.html)

## Git and GitHub

- [Pro Git](https://git-scm.com/book/en/v2), especially [Working with remotes](https://git-scm.com/book/en/v2/Git-Basics-Working-with-Remotes) and [Rebasing](https://git-scm.com/book/en/v2/Git-Branching-Rebasing)
- [`git cherry-pick`](https://git-scm.com/docs/git-cherry-pick), including `-x`
- [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/)
- [GitHub: working with forks](https://docs.github.com/en/pull-requests/collaborating-with-pull-requests/working-with-forks)
- [GitHub: about pull requests](https://docs.github.com/en/pull-requests/collaborating-with-pull-requests/proposing-changes-to-your-work-with-pull-requests/about-pull-requests)
- [GitHub Actions: building and testing Java with Maven](https://docs.github.com/en/actions/use-cases-and-examples/building-and-testing/building-and-testing-java-with-maven)

## Testing

- [JUnit 5 user guide](https://junit.org/junit5/docs/current/user-guide/)
- [Reactor testing](https://projectreactor.io/docs/core/release/reference/testing.html): `StepVerifier`, virtual time, `TestPublisher`
- [Mockito](https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html)
- [JaCoCo Maven plugin](https://www.jacoco.org/jacoco/trunk/doc/maven.html)
- [Testcontainers for Java](https://java.testcontainers.org/)
- [BlockHound](https://github.com/reactor/BlockHound)
- [PIT mutation testing](https://pitest.org/quickstart/maven/)
