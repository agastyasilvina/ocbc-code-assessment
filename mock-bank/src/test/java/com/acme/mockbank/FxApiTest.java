package com.acme.mockbank;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class FxApiTest extends MockBankTestSupport {

  @Test
  void returnsBaseRateValidForThirtySeconds() {
    JsonNode rate = get("/fx/rates?base=USD&quote=IDR").json();

    assertEquals("USD", rate.path("base").asText());
    assertEquals("IDR", rate.path("quote").asText());
    assertEquals("16250.5037", rate.path("rate").asText());
    assertEquals(clock.instant().plusSeconds(30), Instant.parse(rate.path("validUntil").asText()));
  }

  @Test
  void inverseRatesHaveEightDecimalPlaces() {
    assertEquals("0.00006154", get("/fx/rates?base=IDR&quote=USD").json().path("rate").asText());
    assertEquals("0.74454620", get("/fx/rates?base=SGD&quote=USD").json().path("rate").asText());
  }

  @Test
  void throttlesMoreThanFiveCallsPerSecond() {
    for (int i = 0; i < 5; i++) {
      assertEquals(200, get("/fx/rates?base=USD&quote=IDR").status());
    }

    Reply throttled = get("/fx/rates?base=USD&quote=IDR");
    assertEquals(429, throttled.status());
    assertEquals("1", throttled.header("Retry-After"));

    clock.advance(Duration.ofSeconds(1));
    assertEquals(200, get("/fx/rates?base=USD&quote=IDR").status());
  }

  @Test
  void fixedRateOverride() {
    put("/__admin/overrides", """
        {"fx": {"fixedRates": {"USD/IDR": "15000.12345678"}}}
        """);

    assertEquals("15000.12345678", get("/fx/rates?base=USD&quote=IDR").json().path("rate").asText());
    assertEquals("12100.2519", get("/fx/rates?base=SGD&quote=IDR").json().path("rate").asText());
  }

  @Test
  void failsTheNextCalls() {
    put("/__admin/overrides", """
        {"fx": {"failNext": 2}}
        """);

    assertEquals(500, get("/fx/rates?base=USD&quote=IDR").status());
    assertEquals(500, get("/fx/rates?base=USD&quote=IDR").status());
    assertEquals(200, get("/fx/rates?base=USD&quote=IDR").status());
  }

  @Test
  void rejectsUnsupportedPairs() {
    assertEquals(400, get("/fx/rates?base=USD&quote=USD").status());
    assertEquals(400, get("/fx/rates?base=EUR&quote=IDR").status());
  }

  @Test
  void recordsIssuedRatesAndCallsPerPair() {
    get("/fx/rates?base=USD&quote=IDR");
    get("/fx/rates?base=USD&quote=IDR");

    JsonNode fx = stats().path("fx");
    assertEquals(2, fx.path("callsByKey").path("USD/IDR").size());
    JsonNode issued = fx.path("ratesIssued").get(0);
    assertEquals("USD/IDR", issued.path("pair").asText());
    assertEquals(clock.millis() + 30_000, issued.path("validUntil").asLong());
  }
}
