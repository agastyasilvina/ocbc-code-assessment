package com.acme.transfer.kit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

/**
 * WIP: fires 100 transfers at a service running on localhost:8080 and checks that they complete.
 * Flaky on CI.
 */
class EndToEndLoadTest {

  @Test
  void hundredTransfers() throws Exception {
    HttpClient client = HttpClient.newHttpClient();
    List<Integer> statuses = new CopyOnWriteArrayList<>();
    for (int i = 0; i < 100; i++) {
      int n = i;
      new Thread(() -> {
        try {
          HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:8080/api/v1/transfers"))
              .header("Content-Type", "application/json")
              .header("Idempotency-Key", "load-" + System.currentTimeMillis() + "-" + n)
              .POST(HttpRequest.BodyPublishers.ofString(
                  "{\"sourceAccount\":\"2000000001\",\"destinationAccount\":\"2000000002\","
                      + "\"amount\":\"1.00\",\"currency\":\"USD\"}"))
              .build();
          statuses.add(client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
        } catch (Exception e) {
          statuses.add(-1);
        }
      }).start();
    }

    Thread.sleep(10_000); // should be enough for everything to finish

    assertEquals(100, statuses.size());
    assertTrue(statuses.stream().allMatch(status -> status == 201));
  }
}
