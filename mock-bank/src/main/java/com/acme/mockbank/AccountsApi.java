package com.acme.mockbank;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** {@code GET /accounts/{accountNo}}. More than 20 calls in flight get 429 with Retry-After. */
final class AccountsApi implements Service {

  private final Settings settings;
  private final CallStats stats = new CallStats();

  AccountsApi(Settings settings) {
    this.settings = settings;
  }

  @Override
  public String name() {
    return "accounts";
  }

  @Override
  public void reset() {
    stats.reset();
  }

  @Override
  public Map<String, Object> stats() {
    return stats.snapshot();
  }

  void handle(HttpExchange exchange) throws Exception {
    if (!"GET".equals(exchange.getRequestMethod())) {
      Http.json(exchange, 405, Http.error("METHOD_NOT_ALLOWED"));
      return;
    }
    String accountNo = Http.pathAfter(exchange, "/accounts/");
    int inFlight = stats.begin(accountNo, settings.now());
    int status = 500;
    try {
      status = respond(exchange, accountNo, inFlight);
    } finally {
      stats.end(status);
    }
  }

  private int respond(HttpExchange exchange, String accountNo, int inFlight) throws Exception {
    Chaos.Accounts chaos = settings.chaos().accounts;
    if (inFlight > chaos.maxConcurrent) {
      return Http.fail(exchange, 429, 1);
    }
    Overrides.Response scripted = settings.overrides().nextAccountResponse(accountNo);
    if (scripted == null) {
      Http.sleep(settings.pick(chaos.latencyMs));
      if (settings.chance(chaos.errorRate)) {
        return Http.fail(exchange, 503, null);
      }
      return sendAccount(exchange, accountNo, false);
    }
    Http.sleep(scripted.latencyMs != null ? scripted.latencyMs : settings.pick(chaos.latencyMs));
    if (scripted.status != 200) {
      return Http.fail(exchange, scripted.status, scripted.retryAfter);
    }
    return sendAccount(exchange, accountNo, scripted.malformed);
  }

  private static int sendAccount(HttpExchange exchange, String accountNo, boolean malformed)
      throws IOException {
    Optional<Fixtures.Account> found = Fixtures.account(accountNo);
    if (found.isEmpty()) {
      Http.json(exchange, 404, Http.error("ACCOUNT_NOT_FOUND"));
      return 404;
    }
    if (malformed) {
      Http.raw(exchange, 200, "{\"accountNo\":\"" + accountNo + "\",\"currency\":");
      return 200;
    }
    Fixtures.Account account = found.get();
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("accountNo", account.accountNo());
    body.put("name", account.name());
    body.put("currency", account.currency());
    body.put("status", account.status());
    body.put("available", account.available().toPlainString());
    Http.json(exchange, 200, body);
    return 200;
  }
}
