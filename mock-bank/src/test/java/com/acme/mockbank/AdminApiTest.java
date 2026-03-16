package com.acme.mockbank;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

class AdminApiTest extends MockBankTestSupport {

  @Test
  void healthIsUp() {
    Reply reply = get("/health");

    assertEquals(200, reply.status());
    assertEquals("UP", reply.json().path("status").asText());
  }

  @Test
  void chaosUpdatesAreMerged() {
    put("/__admin/chaos", """
        {"fx": {"errorRate": 0.5, "latencyMs": {"max": 40}}}
        """);

    JsonNode chaos = get("/__admin/chaos").json();
    assertEquals(0.5, chaos.path("fx").path("errorRate").asDouble());
    assertEquals(40, chaos.path("fx").path("latencyMs").path("max").asInt());
    assertEquals(0, chaos.path("fx").path("latencyMs").path("min").asInt());
    assertEquals(5, chaos.path("fx").path("maxPerSecond").asInt());
    assertEquals(20, chaos.path("accounts").path("maxConcurrent").asInt());
  }

  @Test
  void rejectsInvalidChaos() {
    assertEquals(400, put("/__admin/chaos", "{\"fx\": {\"errorRate\": 2}}").status());
    assertEquals(400, put("/__admin/chaos", "{\"accounts\": {\"latencyMs\": {\"min\": 50, \"max\": 10}}}").status());
    assertEquals(400, put("/__admin/chaos", "{\"unknown\": 1}").status());
    assertEquals(0.0, get("/__admin/chaos").json().path("fx").path("errorRate").asDouble());
  }

  @Test
  void rejectsInvalidOverrides() {
    assertEquals(400, put("/__admin/overrides", "{\"core\": {\"applied\": \"sometimes\"}}").status());
    assertEquals(400, put("/__admin/overrides", "{\"fx\": {\"fixedRates\": {\"USD/IDR\": \"-1\"}}}").status());
    assertEquals(400, put("/__admin/overrides", "{\"accounts\": {\"2000000001\": {\"responses\": [{\"status\": 302}]}}}").status());
  }

  @Test
  void resetRestoresTheBaselineAndClearsEverything() {
    put("/__admin/chaos", "{\"fx\": {\"errorRate\": 0.5}}");
    put("/__admin/overrides", "{\"fx\": {\"failNext\": 3}}");
    get("/accounts/2000000001");
    post("/core/sessions", "{}");

    assertEquals(204, post("/__admin/reset", null).status());

    assertEquals(0.0, get("/__admin/chaos").json().path("fx").path("errorRate").asDouble());
    assertEquals(0, get("/__admin/overrides").json().path("remaining").path("fxFailures").asInt());
    JsonNode stats = stats();
    assertEquals(0, stats.path("accounts").path("calls").asInt());
    assertEquals(0, stats.path("core").path("sessions").path("open").asInt());
  }
}
