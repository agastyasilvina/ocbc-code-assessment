package com.acme.mockbank;

import com.sun.net.httpserver.HttpExchange;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;

/**
 * {@code GET /fx/rates?base=USD&quote=IDR}. Rates are valid for 30 seconds. More than 5 calls in
 * one second get 429 with Retry-After.
 */
final class FxApi implements Service {

  private final Settings settings;
  private final CallStats stats = new CallStats();
  private final ReentrantLock windowLock = new ReentrantLock();
  private final Deque<Long> window = new ArrayDeque<>();
  private final Queue<Map<String, Object>> issued = new ConcurrentLinkedQueue<>();

  FxApi(Settings settings) {
    this.settings = settings;
  }

  @Override
  public String name() {
    return "fx";
  }

  @Override
  public void reset() {
    stats.reset();
    issued.clear();
    windowLock.lock();
    try {
      window.clear();
    } finally {
      windowLock.unlock();
    }
  }

  @Override
  public Map<String, Object> stats() {
    Map<String, Object> out = stats.snapshot();
    out.put("ratesIssued", List.copyOf(issued));
    return out;
  }

  void handle(HttpExchange exchange) throws Exception {
    if (!"GET".equals(exchange.getRequestMethod())) {
      Http.json(exchange, 405, Http.error("METHOD_NOT_ALLOWED"));
      return;
    }
    Map<String, String> query = Http.query(exchange);
    String base = query.getOrDefault("base", "").toUpperCase(Locale.ROOT);
    String quote = query.getOrDefault("quote", "").toUpperCase(Locale.ROOT);
    stats.begin(base + "/" + quote, settings.now());
    int status = 500;
    try {
      status = respond(exchange, base, quote);
    } finally {
      stats.end(status);
    }
  }

  private int respond(HttpExchange exchange, String base, String quote) throws Exception {
    Chaos.Fx chaos = settings.chaos().fx;
    if (!admit(chaos.maxPerSecond)) {
      return Http.fail(exchange, 429, 1);
    }
    OverrideState overrides = settings.overrides();
    Integer scriptedFailure = overrides.nextFxFailure();
    Http.sleep(settings.pick(chaos.latencyMs));
    if (scriptedFailure != null) {
      return Http.fail(exchange, scriptedFailure, overrides.fxRetryAfter());
    }
    if (settings.chance(chaos.errorRate)) {
      return Http.fail(exchange, 500, null);
    }
    if (!Fixtures.CURRENCIES.contains(base) || !Fixtures.CURRENCIES.contains(quote)
        || base.equals(quote)) {
      Http.json(exchange, 400, Http.error("UNSUPPORTED_PAIR", "base and quote must be two different "
          + "currencies out of IDR, USD and SGD"));
      return 400;
    }
    String pair = base + "/" + quote;
    BigDecimal rate = overrides.fixedRate(pair).orElseGet(() -> Fixtures.rate(base, quote).orElseThrow());
    Instant issuedAt = settings.instant();
    Instant validUntil = issuedAt.plusSeconds(chaos.validitySeconds);

    Map<String, Object> issuedRate = new LinkedHashMap<>();
    issuedRate.put("pair", pair);
    issuedRate.put("rate", rate.toPlainString());
    issuedRate.put("issuedAt", issuedAt.toEpochMilli());
    issuedRate.put("validUntil", validUntil.toEpochMilli());
    issued.add(issuedRate);

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("base", base);
    body.put("quote", quote);
    body.put("rate", rate.toPlainString());
    body.put("validUntil", validUntil.toString());
    Http.json(exchange, 200, body);
    return 200;
  }

  /** Sliding one-second window over admitted calls. */
  private boolean admit(int maxPerSecond) {
    long now = settings.now();
    windowLock.lock();
    try {
      while (!window.isEmpty() && window.peekFirst() <= now - 1000) {
        window.pollFirst();
      }
      if (window.size() >= maxPerSecond) {
        return false;
      }
      window.addLast(now);
      return true;
    } finally {
      windowLock.unlock();
    }
  }
}
