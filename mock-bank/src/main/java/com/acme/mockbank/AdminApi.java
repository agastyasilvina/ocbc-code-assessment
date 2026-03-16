package com.acme.mockbank;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** {@code /__admin/*}: reset, chaos, overrides and statistics. */
final class AdminApi {

  private final Settings settings;
  private final List<Service> services;

  AdminApi(Settings settings, List<Service> services) {
    this.settings = settings;
    this.services = services;
  }

  void handle(HttpExchange exchange) throws Exception {
    String route = exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath();
    switch (route) {
      case "POST /__admin/reset" -> {
        settings.reset();
        services.forEach(Service::reset);
        Http.noContent(exchange);
      }
      case "GET /__admin/chaos" -> Http.json(exchange, 200, settings.chaos());
      case "PUT /__admin/chaos" -> {
        Chaos next = Chaos.merge(settings.chaos(), Http.body(exchange));
        settings.chaos(next);
        Http.json(exchange, 200, next);
      }
      case "GET /__admin/overrides" -> Http.json(exchange, 200, settings.overrides().snapshot());
      case "PUT /__admin/overrides" -> {
        OverrideState next = new OverrideState(parseOverrides(Http.body(exchange)));
        settings.overrides(next);
        Http.json(exchange, 200, next.snapshot());
      }
      case "DELETE /__admin/overrides" -> {
        settings.overrides(OverrideState.none());
        Http.noContent(exchange);
      }
      case "GET /__admin/stats" -> Http.json(exchange, 200, stats());
      default -> Http.json(exchange, 404, Http.error("NOT_FOUND"));
    }
  }

  private Map<String, Object> stats() {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("at", settings.now());
    for (Service service : services) {
      out.put(service.name(), service.stats());
    }
    return out;
  }

  private static Overrides parseOverrides(byte[] body) {
    try {
      Overrides overrides = Http.JSON.readValue(body, Overrides.class);
      if (overrides == null) {
        throw new BadRequest("overrides must be a JSON object");
      }
      return overrides;
    } catch (JsonProcessingException e) {
      throw new BadRequest("invalid overrides: " + e.getOriginalMessage());
    } catch (IOException e) {
      throw new BadRequest("invalid overrides");
    }
  }
}
