package com.acme.mockbank;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * {@code POST /fraud/assessments}. Amounts ending in .66 are denied and .77 go to review; the
 * decision for a transfer id never changes.
 */
final class FraudApi implements Service {

  private final Settings settings;
  private final CallStats stats = new CallStats();
  private final Map<String, Decision> decisions = new ConcurrentHashMap<>();
  private final Map<String, AtomicLong> answered = new ConcurrentHashMap<>();

  FraudApi(Settings settings) {
    this.settings = settings;
  }

  private record Decision(String decision, double score) {
  }

  @Override
  public String name() {
    return "fraud";
  }

  @Override
  public void reset() {
    stats.reset();
    decisions.clear();
    answered.clear();
  }

  @Override
  public Map<String, Object> stats() {
    Map<String, Object> out = stats.snapshot();
    Map<String, Long> byDecision = new TreeMap<>();
    answered.forEach((decision, count) -> byDecision.put(decision, count.get()));
    out.put("decisions", byDecision);
    return out;
  }

  void handle(HttpExchange exchange) throws Exception {
    if (!"POST".equals(exchange.getRequestMethod())) {
      Http.json(exchange, 405, Http.error("METHOD_NOT_ALLOWED"));
      return;
    }
    JsonNode body = Http.readJson(exchange);
    String transferId = Http.text(body, "transferId");
    stats.begin(transferId, settings.now());
    int status = 500;
    try {
      status = respond(exchange, transferId, Http.text(body, "amount"));
    } catch (BadRequest e) {
      status = 400;
      throw e;
    } finally {
      stats.end(status);
    }
  }

  private int respond(HttpExchange exchange, String transferId, String amountText)
      throws Exception {
    if (transferId == null || transferId.isBlank()) {
      throw new BadRequest("transferId is required");
    }
    if (amountText == null) {
      throw new BadRequest("amount is required");
    }
    String amount = OverrideState.normalizeAmount(amountText);
    Chaos.Fraud chaos = settings.chaos().fraud;
    Overrides.FraudBehaviour scripted = settings.overrides().fraud(amount);
    if (scripted != null) {
      Http.sleep(scripted.hangMs != null ? scripted.hangMs : settings.pick(chaos.latencyMs));
      if (scripted.status != null) {
        return Http.fail(exchange, scripted.status, null);
      }
      if (scripted.decision != null) {
        return answer(exchange, transferId, new Decision(scripted.decision, score(scripted.decision)));
      }
    } else {
      Http.sleep(settings.chance(chaos.slowRate) ? chaos.slowMs : settings.pick(chaos.latencyMs));
      if (settings.chance(chaos.errorRate)) {
        return Http.fail(exchange, 503, null);
      }
    }
    return answer(exchange, transferId, decisions.computeIfAbsent(transferId, id -> decide(amount)));
  }

  private int answer(HttpExchange exchange, String transferId, Decision decision)
      throws IOException {
    answered.computeIfAbsent(decision.decision(), d -> new AtomicLong()).incrementAndGet();
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("transferId", transferId);
    body.put("decision", decision.decision());
    body.put("score", decision.score());
    Http.json(exchange, 200, body);
    return 200;
  }

  private static Decision decide(String amount) {
    if (amount.endsWith(".66")) {
      return new Decision("DENY", score("DENY"));
    }
    if (amount.endsWith(".77")) {
      return new Decision("REVIEW", score("REVIEW"));
    }
    return new Decision("ALLOW", score("ALLOW"));
  }

  private static double score(String decision) {
    return switch (decision) {
      case "DENY" -> 0.97;
      case "REVIEW" -> 0.62;
      default -> 0.08;
    };
  }
}
