package com.acme.mockbank;

import java.time.Clock;
import java.time.Instant;
import java.util.Random;

/** Current chaos, overrides, clock and randomness, shared by every service. */
final class Settings {

  private final Clock clock;
  private final Chaos baseline;
  private volatile Chaos chaos;
  private volatile Random random;
  private volatile OverrideState overrides = OverrideState.none();

  Settings(Clock clock, Chaos baseline) {
    this.clock = clock;
    this.baseline = Chaos.copy(baseline);
    chaos(Chaos.copy(baseline));
  }

  long now() {
    return clock.millis();
  }

  Instant instant() {
    return clock.instant();
  }

  Chaos chaos() {
    return chaos;
  }

  /** Replaces the chaos settings and restarts the random sequence from their seed. */
  void chaos(Chaos next) {
    random = new Random(next.seed);
    chaos = next;
  }

  OverrideState overrides() {
    return overrides;
  }

  void overrides(OverrideState next) {
    overrides = next;
  }

  /** Back to the baseline chaos, with no overrides. */
  void reset() {
    chaos(Chaos.copy(baseline));
    overrides = OverrideState.none();
  }

  boolean chance(double rate) {
    return rate > 0 && random.nextDouble() < rate;
  }

  long pick(Chaos.Range range) {
    return range.max <= range.min ? range.min : random.nextInt(range.min, range.max + 1);
  }
}
