package com.acme.mockbank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;

class AccountsApiTest extends MockBankTestSupport {

  @Test
  void returnsActiveAccount() {
    Reply reply = get("/accounts/2000000001");

    assertEquals(200, reply.status());
    JsonNode account = reply.json();
    assertEquals("2000000001", account.path("accountNo").asText());
    assertEquals("USD", account.path("currency").asText());
    assertEquals("ACTIVE", account.path("status").asText());
    assertEquals("100000.00", account.path("available").asText());
  }

  @Test
  void followsFixtureRules() {
    assertEquals("DORMANT", get("/accounts/1000000007").json().path("status").asText());
    assertEquals("BLOCKED", get("/accounts/1000000008").json().path("status").asText());
    assertEquals(404, get("/accounts/1000000009").status());
    assertEquals(404, get("/accounts/4000000001").status());
    assertEquals(404, get("/accounts/12345").status());
    assertEquals("1000000000.00", get("/accounts/1000000001").json().path("available").asText());

    JsonNode lowBalance = get("/accounts/3000000011").json();
    assertEquals("SGD", lowBalance.path("currency").asText());
    assertEquals("10.00", lowBalance.path("available").asText());
  }

  @Test
  void scriptedResponsesAreUsedInOrder() {
    put("/__admin/overrides", """
        {"accounts": {"2000000001": {"responses": [{"status": 503}, {"status": 503}, {"status": 200}]}}}
        """);

    assertEquals(503, get("/accounts/2000000001").status());
    assertEquals(503, get("/accounts/2000000001").status());
    assertEquals(200, get("/accounts/2000000001").status());
    assertEquals(200, get("/accounts/2000000001").status());
    assertEquals(200, get("/accounts/2000000002").status());
  }

  @Test
  void throttlesOnceWithRetryAfter() {
    put("/__admin/overrides", """
        {"accounts": {"2000000002": {"responses": [{"status": 429, "retryAfter": 1}]}}}
        """);

    Reply throttled = get("/accounts/2000000002");
    assertEquals(429, throttled.status());
    assertEquals("1", throttled.header("Retry-After"));
    assertEquals(200, get("/accounts/2000000002").status());
  }

  @Test
  void thenAppliesToEveryLaterCall() {
    put("/__admin/overrides", """
        {"accounts": {"2000000003": {"then": {"status": 503}}}}
        """);

    assertEquals(503, get("/accounts/2000000003").status());
    assertEquals(503, get("/accounts/2000000003").status());
  }

  @Test
  void canSendAnUnreadableBody() {
    put("/__admin/overrides", """
        {"accounts": {"2000000004": {"responses": [{"status": 200, "malformed": true}]}}}
        """);

    Reply reply = get("/accounts/2000000004");
    assertEquals(200, reply.status());
    assertThrows(UncheckedIOException.class, reply::json);
  }

  @Test
  void rejectsMoreThanTwentyConcurrentCallsAndRecordsThePeak() throws InterruptedException {
    put("/__admin/chaos", """
        {"accounts": {"latencyMs": {"min": 1000, "max": 1000}}}
        """);
    int callers = 30;
    CountDownLatch start = new CountDownLatch(1);
    Queue<Reply> replies = new ConcurrentLinkedQueue<>();
    List<Thread> threads = new ArrayList<>();
    for (int i = 0; i < callers; i++) {
      threads.add(Thread.ofVirtual().start(() -> {
        try {
          start.await();
          replies.add(get("/accounts/2000000001"));
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }));
    }
    start.countDown();
    for (Thread thread : threads) {
      thread.join();
    }

    assertEquals(callers, replies.size());
    List<Reply> throttled = replies.stream().filter(reply -> reply.status() == 429).toList();
    assertFalse(throttled.isEmpty());
    throttled.forEach(reply -> assertEquals("1", reply.header("Retry-After")));
    JsonNode accounts = stats().path("accounts");
    assertTrue(accounts.path("peakConcurrency").asInt() > 20);
    assertEquals(throttled.size(), accounts.path("statuses").path("429").asInt());
    assertEquals(callers, accounts.path("callsByKey").path("2000000001").size());
  }
}
