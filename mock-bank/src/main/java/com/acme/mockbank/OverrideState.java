package com.acme.mockbank;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/** Validated overrides plus what is left of each script. Safe to use from many threads. */
final class OverrideState {

  private static final Set<String> DECISIONS = Set.of("ALLOW", "DENY", "REVIEW");
  private static final Set<String> APPLIED = Set.of("all", "none", "alternate");

  private final Overrides source;
  private final Map<String, Queue<Overrides.Response>> accountResponses = new ConcurrentHashMap<>();
  private final Map<String, BigDecimal> fixedRates = new HashMap<>();
  private final Map<String, Overrides.FraudBehaviour> fraudByAmount = new HashMap<>();
  private final AtomicInteger fxFailuresLeft;
  private final AtomicInteger coreHangsLeft;
  private final AtomicInteger coreHangsIssued = new AtomicInteger();

  OverrideState(Overrides source) {
    if (source == null || source.accounts == null || source.fx == null || source.fraud == null
        || source.core == null) {
      throw new BadRequest("override sections must not be null");
    }
    this.source = source;

    source.accounts.forEach((accountNo, script) -> {
      if (script == null) {
        throw new BadRequest("accounts." + accountNo + " must not be null");
      }
      List<Overrides.Response> responses = script.responses == null ? List.of() : script.responses;
      responses.forEach(response -> validate("accounts." + accountNo, response));
      if (script.then != null) {
        validate("accounts." + accountNo + ".then", script.then);
      }
      accountResponses.put(accountNo, new ConcurrentLinkedQueue<>(responses));
    });

    if (source.fx.fixedRates != null) {
      source.fx.fixedRates.forEach((pair, rate) ->
          fixedRates.put(pair.toUpperCase(Locale.ROOT), positiveDecimal("fx.fixedRates." + pair, rate)));
    }
    if (source.fx.failNext < 0) {
      throw new BadRequest("fx.failNext must not be negative");
    }
    failureStatus("fx.failStatus", source.fx.failStatus);

    if (source.fraud.byAmount != null) {
      source.fraud.byAmount.forEach((amount, behaviour) -> {
        if (behaviour == null) {
          throw new BadRequest("fraud.byAmount." + amount + " must not be null");
        }
        if (behaviour.status != null) {
          failureStatus("fraud.byAmount." + amount + ".status", behaviour.status);
        }
        if (behaviour.decision != null && !DECISIONS.contains(behaviour.decision)) {
          throw new BadRequest("fraud.byAmount." + amount + ".decision must be ALLOW, DENY or REVIEW");
        }
        if (behaviour.hangMs != null && behaviour.hangMs < 0) {
          throw new BadRequest("fraud.byAmount." + amount + ".hangMs must not be negative");
        }
        fraudByAmount.put(normalizeAmount(amount), behaviour);
      });
    }

    if (source.core.hangNext < 0) {
      throw new BadRequest("core.hangNext must not be negative");
    }
    if (source.core.applied == null || !APPLIED.contains(source.core.applied)) {
      throw new BadRequest("core.applied must be all, none or alternate");
    }
    if (source.core.hangMs != null && source.core.hangMs < 0) {
      throw new BadRequest("core.hangMs must not be negative");
    }

    fxFailuresLeft = new AtomicInteger(source.fx.failNext);
    coreHangsLeft = new AtomicInteger(source.core.hangNext);
  }

  static OverrideState none() {
    return new OverrideState(new Overrides());
  }

  /** The next scripted answer for this account, or null to behave normally. */
  Overrides.Response nextAccountResponse(String accountNo) {
    Queue<Overrides.Response> queue = accountResponses.get(accountNo);
    if (queue == null) {
      return null;
    }
    Overrides.Response next = queue.poll();
    return next != null ? next : source.accounts.get(accountNo).then;
  }

  Optional<BigDecimal> fixedRate(String pair) {
    return Optional.ofNullable(fixedRates.get(pair));
  }

  /** The status the next rate call fails with, or null when no failure is scripted. */
  Integer nextFxFailure() {
    return takeOne(fxFailuresLeft) ? Integer.valueOf(source.fx.failStatus) : null;
  }

  Integer fxRetryAfter() {
    return source.fx.retryAfter;
  }

  Overrides.FraudBehaviour fraud(String normalizedAmount) {
    return fraudByAmount.get(normalizedAmount);
  }

  /** Null when the next posting does not hang; otherwise whether the hung posting is applied. */
  Boolean nextCoreHang() {
    if (!takeOne(coreHangsLeft)) {
      return null;
    }
    int index = coreHangsIssued.getAndIncrement();
    return switch (source.core.applied) {
      case "all" -> Boolean.TRUE;
      case "none" -> Boolean.FALSE;
      default -> index % 2 == 0;
    };
  }

  Integer coreHangMs() {
    return source.core.hangMs;
  }

  Map<String, Object> snapshot() {
    Map<String, Integer> accounts = new TreeMap<>();
    accountResponses.forEach((accountNo, queue) -> accounts.put(accountNo, queue.size()));
    Map<String, Object> remaining = new LinkedHashMap<>();
    remaining.put("accountResponses", accounts);
    remaining.put("fxFailures", fxFailuresLeft.get());
    remaining.put("coreHangs", coreHangsLeft.get());
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("overrides", source);
    snapshot.put("remaining", remaining);
    return snapshot;
  }

  /** An amount with exactly two decimal places, the way overrides and decisions are keyed. */
  static String normalizeAmount(String amount) {
    try {
      return new BigDecimal(amount.trim()).setScale(2, RoundingMode.HALF_EVEN).toPlainString();
    } catch (NumberFormatException | NullPointerException e) {
      throw new BadRequest("invalid amount: " + amount);
    }
  }

  private static boolean takeOne(AtomicInteger counter) {
    return counter.getAndUpdate(n -> n > 0 ? n - 1 : 0) > 0;
  }

  private static void validate(String name, Overrides.Response response) {
    if (response == null) {
      throw new BadRequest(name + " must not contain null responses");
    }
    if (response.status != 200) {
      failureStatus(name + ".status", response.status);
    }
    if (response.retryAfter != null && response.retryAfter < 0) {
      throw new BadRequest(name + ".retryAfter must not be negative");
    }
    if (response.latencyMs != null && response.latencyMs < 0) {
      throw new BadRequest(name + ".latencyMs must not be negative");
    }
  }

  private static void failureStatus(String name, int status) {
    if (status < 400 || status > 599) {
      throw new BadRequest(name + " must be between 400 and 599");
    }
  }

  private static BigDecimal positiveDecimal(String name, String value) {
    try {
      BigDecimal decimal = new BigDecimal(value.trim());
      if (decimal.signum() > 0) {
        return decimal;
      }
    } catch (NumberFormatException | NullPointerException e) {
      // reported below
    }
    throw new BadRequest(name + " must be a positive decimal string");
  }
}
