package com.acme.mockbank;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Small helpers around the JDK HTTP server. */
final class Http {

  static final ObjectMapper JSON = new ObjectMapper();

  private Http() {
  }

  @FunctionalInterface
  interface Endpoint {
    void handle(HttpExchange exchange) throws Exception;
  }

  /**
   * Wraps an endpoint: bad input becomes 400, anything unexpected becomes 500, and the exchange is
   * always closed.
   */
  static HttpHandler handler(Endpoint endpoint) {
    return exchange -> {
      try {
        endpoint.handle(exchange);
      } catch (BadRequest e) {
        trySend(exchange, 400, error("BAD_REQUEST", e.getMessage()));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } catch (IOException e) {
        // The client went away, usually after its own timeout. Nothing left to send.
      } catch (Exception e) {
        trySend(exchange, 500, error("MOCK_BANK_ERROR", String.valueOf(e.getMessage())));
      } finally {
        exchange.close();
      }
    };
  }

  static Map<String, Object> error(String code) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("error", code);
    return body;
  }

  static Map<String, Object> error(String code, String message) {
    Map<String, Object> body = error(code);
    body.put("message", message);
    return body;
  }

  static void json(HttpExchange exchange, int status, Object body) throws IOException {
    json(exchange, status, body, Map.of());
  }

  static void json(HttpExchange exchange, int status, Object body, Map<String, String> headers)
      throws IOException {
    send(exchange, status, JSON.writeValueAsBytes(body), headers);
  }

  /** Sends {@code body} as is, even when it is not valid JSON. */
  static void raw(HttpExchange exchange, int status, String body) throws IOException {
    send(exchange, status, body.getBytes(StandardCharsets.UTF_8), Map.of());
  }

  static void noContent(HttpExchange exchange) throws IOException {
    exchange.sendResponseHeaders(204, -1);
  }

  /** Sends a failure status. 429 always carries {@code Retry-After}, 1 second unless given. */
  static int fail(HttpExchange exchange, int status, Integer retryAfter) throws IOException {
    Integer seconds = retryAfter == null && status == 429 ? Integer.valueOf(1) : retryAfter;
    Map<String, String> headers =
        seconds == null ? Map.of() : Map.of("Retry-After", seconds.toString());
    json(exchange, status, error(codeFor(status)), headers);
    return status;
  }

  private static String codeFor(int status) {
    return switch (status) {
      case 400 -> "BAD_REQUEST";
      case 404 -> "NOT_FOUND";
      case 429 -> "TOO_MANY_REQUESTS";
      case 500 -> "INTERNAL_ERROR";
      case 502 -> "BAD_GATEWAY";
      case 503 -> "SERVICE_UNAVAILABLE";
      case 504 -> "GATEWAY_TIMEOUT";
      default -> "HTTP_" + status;
    };
  }

  static byte[] body(HttpExchange exchange) throws IOException {
    return exchange.getRequestBody().readAllBytes();
  }

  static JsonNode readJson(HttpExchange exchange) throws IOException {
    byte[] bytes = body(exchange);
    JsonNode node;
    try {
      node = JSON.readTree(bytes);
    } catch (JsonProcessingException e) {
      throw new BadRequest("request body is not valid JSON");
    }
    if (node == null || !node.isObject()) {
      throw new BadRequest("request body must be a JSON object");
    }
    return node;
  }

  /** A field as text, or null when it is missing or null. */
  static String text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return value == null || value.isNull() ? null : value.asText();
  }

  static Map<String, String> query(HttpExchange exchange) {
    Map<String, String> params = new HashMap<>();
    String raw = exchange.getRequestURI().getRawQuery();
    if (raw == null || raw.isEmpty()) {
      return params;
    }
    for (String pair : raw.split("&")) {
      int eq = pair.indexOf('=');
      String name = eq < 0 ? pair : pair.substring(0, eq);
      String value = eq < 0 ? "" : pair.substring(eq + 1);
      params.put(URLDecoder.decode(name, StandardCharsets.UTF_8),
          URLDecoder.decode(value, StandardCharsets.UTF_8));
    }
    return params;
  }

  /** The decoded path after {@code prefix}, or an empty string. */
  static String pathAfter(HttpExchange exchange, String prefix) {
    String path = exchange.getRequestURI().getPath();
    return path.startsWith(prefix) ? path.substring(prefix.length()) : "";
  }

  static void sleep(long millis) throws InterruptedException {
    if (millis > 0) {
      Thread.sleep(millis);
    }
  }

  private static void send(HttpExchange exchange, int status, byte[] bytes,
                           Map<String, String> headers) throws IOException {
    headers.forEach((name, value) -> exchange.getResponseHeaders().set(name, value));
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
    if (bytes.length > 0) {
      try (OutputStream out = exchange.getResponseBody()) {
        out.write(bytes);
      }
    }
  }

  private static void trySend(HttpExchange exchange, int status, Object body) {
    try {
      json(exchange, status, body);
    } catch (IOException | RuntimeException ignored) {
      // Headers were already sent or the client is gone.
    }
  }
}
