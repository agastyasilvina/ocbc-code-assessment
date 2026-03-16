package com.acme.mockbank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FraudApiTest extends MockBankTestSupport {

  @Test
  void decisionsFollowTheAmountHooks() {
    assertEquals("DENY", assess("T-1", "150.66").json().path("decision").asText());
    assertEquals("REVIEW", assess("T-2", "150.77").json().path("decision").asText());
    assertEquals("ALLOW", assess("T-3", "150.00").json().path("decision").asText());
  }

  @Test
  void theDecisionForATransferIdNeverChanges() {
    assertEquals("DENY", assess("T-4", "150.66").json().path("decision").asText());
    assertEquals("DENY", assess("T-4", "150.00").json().path("decision").asText());
  }

  @Test
  void overrideFailsOneAmount() {
    put("/__admin/overrides", """
        {"fraud": {"byAmount": {"7000.00": {"status": 503}}}}
        """);

    assertEquals(503, assess("T-5", "7000.00").status());
    assertEquals(200, assess("T-6", "7000.01").status());
  }

  @Test
  void overrideDelaysOneAmount() {
    put("/__admin/overrides", """
        {"fraud": {"byAmount": {"8000.00": {"hangMs": 300}}}}
        """);

    long started = System.nanoTime();
    Reply reply = assess("T-7", "8000");
    long elapsedMillis = (System.nanoTime() - started) / 1_000_000;

    assertEquals("ALLOW", reply.json().path("decision").asText());
    assertTrue(elapsedMillis >= 300, "answered after " + elapsedMillis + " ms");
  }

  @Test
  void requiresTransferIdAndAmount() {
    assertEquals(400, post("/fraud/assessments", "{\"amount\": \"10.00\"}").status());
    assertEquals(400, post("/fraud/assessments", "{\"transferId\": \"T-8\"}").status());
    assertEquals(400, post("/fraud/assessments", "not json").status());
  }

  @Test
  void recordsCallsPerTransferId() {
    assess("T-9", "10.00");
    assess("T-9", "10.00");

    assertEquals(2, stats().path("fraud").path("callsByKey").path("T-9").size());
  }

  private Reply assess(String transferId, String amount) {
    return post("/fraud/assessments", """
        {"transferId": "%s", "amount": "%s", "currency": "USD",
         "sourceAccount": "2000000001", "destinationAccount": "1000000002"}
        """.formatted(transferId, amount));
  }
}
