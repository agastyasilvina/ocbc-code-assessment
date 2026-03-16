package com.acme.mockbank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CoreApiTest extends MockBankTestSupport {

  @Test
  void allowsAtMostTenOpenSessions() {
    String first = openSession();
    for (int i = 1; i < 10; i++) {
      openSession();
    }

    Reply busy = post("/core/sessions", "{}");
    assertEquals(503, busy.status());
    assertEquals("BUSY", busy.json().path("error").asText());

    assertEquals(204, send("DELETE", "/core/sessions/" + first, null, Map.of()).status());
    assertEquals(201, post("/core/sessions", "{}").status());

    JsonNode sessions = stats().path("core").path("sessions");
    assertEquals(10, sessions.path("open").asInt());
    assertEquals(10, sessions.path("peakOpen").asInt());
    assertEquals(1, sessions.path("busyRejections").asInt());
    assertEquals(1, sessions.path("closed").asInt());
  }

  @Test
  void unclosedSessionsExpireAndCountAsLeaks() {
    for (int i = 0; i < 10; i++) {
      openSession();
    }
    assertEquals(503, post("/core/sessions", "{}").status());

    clock.advance(Duration.ofSeconds(121));

    assertEquals(201, post("/core/sessions", "{}").status());
    assertEquals(10, stats().path("core").path("sessions").path("expiredWithoutClose").asInt());
  }

  @Test
  void closingAnUnknownSessionIsNotFound() {
    assertEquals(404, send("DELETE", "/core/sessions/S999", null, Map.of()).status());
  }

  @Test
  void postsWhenAccountsAndFundsAllowIt() {
    JsonNode posting = postPosting(openSession(), "ref-1", "2000000001", "150.00", "USD",
        "1000000002", "2437575.56", "IDR").json();

    assertEquals("ref-1", posting.path("reference").asText());
    assertEquals("POSTED", posting.path("status").asText());
    assertTrue(posting.path("coreTxnId").asText().startsWith("CT"));
    assertTrue(posting.path("reasonCode").isNull());
  }

  @Test
  void rejectsPerFixtureRules() {
    String session = openSession();

    assertEquals("ACCOUNT_NOT_ACTIVE", reasonCode(postPosting(session, "r-1", "2000000007", "1.00",
        "USD", "2000000002", "1.00", "USD")));
    assertEquals("ACCOUNT_NOT_FOUND", reasonCode(postPosting(session, "r-2", "2000000001", "1.00",
        "USD", "2000000009", "1.00", "USD")));
    assertEquals("INSUFFICIENT_FUNDS", reasonCode(postPosting(session, "r-3", "2000000011", "50.00",
        "USD", "2000000002", "50.00", "USD")));
    assertEquals("CURRENCY_MISMATCH", reasonCode(postPosting(session, "r-4", "2000000001", "1.00",
        "IDR", "2000000002", "1.00", "USD")));
  }

  @Test
  void neverDeduplicatesAndReportsDuplicates() {
    String session = openSession();
    JsonNode first = postPosting(session, "dup-1", "2000000001", "10.00", "USD",
        "2000000002", "10.00", "USD").json();
    JsonNode second = postPosting(session, "dup-1", "2000000001", "10.00", "USD",
        "2000000002", "10.00", "USD").json();

    assertEquals("POSTED", second.path("status").asText());
    assertNotEquals(first.path("coreTxnId").asText(), second.path("coreTxnId").asText());
    JsonNode core = stats().path("core");
    assertEquals("dup-1", core.path("duplicateReferences").get(0).asText());
    assertEquals("dup-1", core.path("repeatedReferences").get(0).asText());
    assertEquals(2, core.path("postings").path("byReference").path("dup-1").size());
  }

  @Test
  void inquiryIsCaseSensitive() {
    String session = openSession();
    postPosting(session, "abc-123", "2000000001", "10.00", "USD", "2000000002", "10.00", "USD");

    assertEquals(200, inquire(session, "abc-123").status());
    assertEquals(404, inquire(session, "ABC-123").status());
    assertEquals(1, stats().path("core").path("inquiries").path("ABC-123").asInt());
  }

  @Test
  void needsAnOpenSession() {
    String body = postingBody("ref-2", "2000000001", "1.00", "USD", "2000000002", "1.00", "USD");
    assertEquals(401, post("/core/postings", body).status());

    String session = openSession();
    send("DELETE", "/core/sessions/" + session, null, Map.of());
    assertEquals(401, send("POST", "/core/postings", body, Map.of("X-Core-Session", session)).status());
    assertEquals(401, inquire(session, "ref-2").status());
  }

  @Test
  void scriptedHangsRecordGroundTruth() {
    put("/__admin/overrides", """
        {"core": {"hangNext": 2, "applied": "alternate", "hangMs": 100}}
        """);
    String session = openSession();

    Reply applied = postPosting(session, "hang-1", "2000000001", "10.00", "USD",
        "2000000002", "10.00", "USD");
    Reply lost = postPosting(session, "hang-2", "2000000001", "10.00", "USD",
        "2000000002", "10.00", "USD");

    assertEquals(200, applied.status());
    assertEquals(504, lost.status());
    assertEquals(200, inquire(session, "hang-1").status());
    assertEquals(404, inquire(session, "hang-2").status());
    JsonNode hung = stats().path("core").path("hung");
    assertEquals(2, hung.size());
    assertEquals("hang-1", hung.get(0).path("reference").asText());
    assertTrue(hung.get(0).path("applied").asBoolean());
    assertEquals("hang-2", hung.get(1).path("reference").asText());
    assertTrue(!hung.get(1).path("applied").asBoolean());
  }

  private String openSession() {
    Reply reply = post("/core/sessions", "{}");
    assertEquals(201, reply.status());
    return reply.json().path("sessionId").asText();
  }

  private Reply postPosting(String session, String reference, String debitAccount,
                            String debitAmount, String debitCurrency, String creditAccount,
                            String creditAmount, String creditCurrency) {
    return send("POST", "/core/postings", postingBody(reference, debitAccount, debitAmount,
        debitCurrency, creditAccount, creditAmount, creditCurrency),
        Map.of("X-Core-Session", session));
  }

  private Reply inquire(String session, String reference) {
    return send("GET", "/core/postings/" + reference, null, Map.of("X-Core-Session", session));
  }

  private static String reasonCode(Reply reply) {
    return reply.json().path("reasonCode").asText();
  }

  private static String postingBody(String reference, String debitAccount, String debitAmount,
                                    String debitCurrency, String creditAccount,
                                    String creditAmount, String creditCurrency) {
    return """
        {"reference": "%s", "debitAccount": "%s", "creditAccount": "%s",
         "debitAmount": "%s", "debitCurrency": "%s",
         "creditAmount": "%s", "creditCurrency": "%s"}
        """.formatted(reference, debitAccount, creditAccount, debitAmount, debitCurrency,
        creditAmount, creditCurrency);
  }
}
