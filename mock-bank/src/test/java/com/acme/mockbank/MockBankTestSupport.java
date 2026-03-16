package com.acme.mockbank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/** Starts a quiet mock bank (no latency, no random failures) on a free port for each test. */
abstract class MockBankTestSupport {

  static final ObjectMapper JSON = new ObjectMapper();

  protected final MutableClock clock = new MutableClock(Instant.parse("2026-03-02T02:00:00Z"));
  protected final HttpClient http =
      HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
  protected MockBank bank;

  @BeforeEach
  void startBank() throws IOException {
    bank = MockBank.start(0, clock, Chaos.quiet());
  }

  @AfterEach
  void stopBank() {
    bank.close();
  }

  protected Reply get(String path) {
    return send("GET", path, null, Map.of());
  }

  protected Reply post(String path, String body) {
    return send("POST", path, body, Map.of());
  }

  protected Reply put(String path, String body) {
    return send("PUT", path, body, Map.of());
  }

  protected Reply send(String method, String path, String body, Map<String, String> headers) {
    HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + bank.port() + path))
        .timeout(Duration.ofSeconds(10))
        .method(method, body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body));
    if (body != null) {
      request.header("Content-Type", "application/json");
    }
    headers.forEach(request::header);
    try {
      HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
      return new Reply(response.statusCode(), response.headers(), response.body());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  protected JsonNode stats() {
    return get("/__admin/stats").json();
  }

  record Reply(int status, HttpHeaders headers, String body) {

    JsonNode json() {
      try {
        return JSON.readTree(body);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    String header(String name) {
      return headers.firstValue(name).orElse(null);
    }
  }
}
