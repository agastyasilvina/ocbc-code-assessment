package com.acme.mockbank;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;

/**
 * Probabilistic failure settings, changed through {@code PUT /__admin/chaos}. Public fields so that
 * Jackson can read, write and merge them.
 */
public final class Chaos {

  private static final ObjectMapper MERGER = new ObjectMapper().setDefaultMergeable(true);

  public long seed = 42;
  public Accounts accounts = new Accounts();
  public Fx fx = new Fx();
  public Fraud fraud = new Fraud();
  public Core core = new Core();

  public static final class Range {
    public int min;
    public int max;

    public Range() {
    }

    Range(int min, int max) {
      this.min = min;
      this.max = max;
    }
  }

  public static final class Accounts {
    public Range latencyMs = new Range(20, 150);
    public double errorRate = 0.03;
    public int maxConcurrent = 20;
  }

  public static final class Fx {
    public Range latencyMs = new Range(100, 300);
    public double errorRate = 0.05;
    public int maxPerSecond = 5;
    public int validitySeconds = 30;
  }

  public static final class Fraud {
    public Range latencyMs = new Range(50, 250);
    public double errorRate = 0.02;
    public double slowRate = 0.05;
    public int slowMs = 2500;
  }

  public static final class Core {
    public Range latencyMs = new Range(100, 300);
    public double hangRate = 0.03;
    public int hangMs = 5000;
    public double applyOnHangRate = 0.5;
    public Range inquiryLatencyMs = new Range(20, 80);
    public int maxSessions = 10;
    public int sessionTtlSeconds = 120;
  }

  /** The mild defaults for local development. */
  static Chaos defaults() {
    return new Chaos();
  }

  /** No latency and no failures. Limits stay as documented. */
  static Chaos quiet() {
    Chaos chaos = new Chaos();
    chaos.accounts.latencyMs = new Range(0, 0);
    chaos.accounts.errorRate = 0;
    chaos.fx.latencyMs = new Range(0, 0);
    chaos.fx.errorRate = 0;
    chaos.fraud.latencyMs = new Range(0, 0);
    chaos.fraud.errorRate = 0;
    chaos.fraud.slowRate = 0;
    chaos.core.latencyMs = new Range(0, 0);
    chaos.core.hangRate = 0;
    chaos.core.inquiryLatencyMs = new Range(0, 0);
    return chaos;
  }

  static Chaos copy(Chaos chaos) {
    return Http.JSON.convertValue(chaos, Chaos.class);
  }

  /** Applies a partial update: fields present in {@code patch} replace the current values. */
  static Chaos merge(Chaos current, byte[] patch) {
    Chaos merged;
    try {
      merged = MERGER.readerForUpdating(copy(current)).readValue(patch);
    } catch (JsonProcessingException e) {
      throw new BadRequest("invalid chaos settings: " + e.getOriginalMessage());
    } catch (IOException e) {
      throw new BadRequest("invalid chaos settings");
    }
    merged.validate();
    return merged;
  }

  void validate() {
    if (accounts == null || fx == null || fraud == null || core == null) {
      throw new BadRequest("chaos sections must not be null");
    }
    range("accounts.latencyMs", accounts.latencyMs);
    rate("accounts.errorRate", accounts.errorRate);
    positive("accounts.maxConcurrent", accounts.maxConcurrent);

    range("fx.latencyMs", fx.latencyMs);
    rate("fx.errorRate", fx.errorRate);
    positive("fx.maxPerSecond", fx.maxPerSecond);
    positive("fx.validitySeconds", fx.validitySeconds);

    range("fraud.latencyMs", fraud.latencyMs);
    rate("fraud.errorRate", fraud.errorRate);
    rate("fraud.slowRate", fraud.slowRate);
    notNegative("fraud.slowMs", fraud.slowMs);

    range("core.latencyMs", core.latencyMs);
    rate("core.hangRate", core.hangRate);
    notNegative("core.hangMs", core.hangMs);
    rate("core.applyOnHangRate", core.applyOnHangRate);
    range("core.inquiryLatencyMs", core.inquiryLatencyMs);
    positive("core.maxSessions", core.maxSessions);
    positive("core.sessionTtlSeconds", core.sessionTtlSeconds);
  }

  private static void range(String name, Range range) {
    if (range == null || range.min < 0 || range.max < range.min) {
      throw new BadRequest(name + " needs 0 <= min <= max");
    }
  }

  private static void rate(String name, double rate) {
    if (rate < 0 || rate > 1) {
      throw new BadRequest(name + " must be between 0 and 1");
    }
  }

  private static void positive(String name, int value) {
    if (value <= 0) {
      throw new BadRequest(name + " must be positive");
    }
  }

  private static void notNegative(String name, int value) {
    if (value < 0) {
      throw new BadRequest(name + " must not be negative");
    }
  }
}
