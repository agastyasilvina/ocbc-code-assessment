package com.acme.core.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** A minimal core for SDK tests: sessions, postings and inquiries, nothing else. */
final class StubCoreServer implements AutoCloseable {

  private final ObjectMapper json = new ObjectMapper();
  private final HttpServer server;
  private final ExecutorService executor = Executors.newCachedThreadPool();
  private final AtomicInteger sessionSeq = new AtomicInteger();
  private final AtomicInteger txnSeq = new AtomicInteger();
  private final Set<String> openSessions = ConcurrentHashMap.newKeySet();
  private final Map<String, String> postings = new ConcurrentHashMap<>();

  final AtomicInteger sessionsOpened = new AtomicInteger();
  final AtomicInteger sessionsClosed = new AtomicInteger();
  volatile boolean busy;
  volatile long postingDelayMillis;

  StubCoreServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext("/core/sessions", this::sessions);
    server.createContext("/core/postings", this::postings);
    server.setExecutor(executor);
    server.start();
  }

  String baseUrl() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  int openSessionCount() {
    return openSessions.size();
  }

  @Override
  public void close() {
    server.stop(0);
    executor.shutdownNow();
  }

  private void sessions(HttpExchange exchange) throws IOException {
    try {
      switch (exchange.getRequestMethod()) {
        case "POST" -> {
          exchange.getRequestBody().readAllBytes();
          if (busy) {
            respond(exchange, 503, "{\"error\":\"BUSY\"}");
            return;
          }
          String id = "S" + sessionSeq.incrementAndGet();
          openSessions.add(id);
          sessionsOpened.incrementAndGet();
          respond(exchange, 201, "{\"sessionId\":\"" + id + "\"}");
        }
        case "DELETE" -> {
          String id = exchange.getRequestURI().getPath().substring("/core/sessions/".length());
          if (openSessions.remove(id)) {
            sessionsClosed.incrementAndGet();
            respond(exchange, 204, null);
          } else {
            respond(exchange, 404, "{\"error\":\"SESSION_NOT_FOUND\"}");
          }
        }
        default -> respond(exchange, 405, "{\"error\":\"METHOD_NOT_ALLOWED\"}");
      }
    } finally {
      exchange.close();
    }
  }

  private void postings(HttpExchange exchange) throws IOException {
    try {
      String session = exchange.getRequestHeaders().getFirst("X-Core-Session");
      if (session == null || !openSessions.contains(session)) {
        respond(exchange, 401, "{\"error\":\"INVALID_SESSION\"}");
        return;
      }
      switch (exchange.getRequestMethod()) {
        case "POST" -> {
          JsonNode body = json.readTree(exchange.getRequestBody().readAllBytes());
          pause(postingDelayMillis);
          String reference = body.path("reference").asText();
          boolean rejected = body.path("debitAccount").asText().endsWith("8");
          ObjectNode result = json.createObjectNode().put("reference", reference);
          if (rejected) {
            result.putNull("coreTxnId").put("status", "REJECTED").put("reasonCode", "ACCOUNT_NOT_ACTIVE");
          } else {
            result.put("coreTxnId", "CT" + txnSeq.incrementAndGet()).put("status", "POSTED")
                .putNull("reasonCode");
          }
          result.put("postedAt", Instant.now().toString());
          String response = json.writeValueAsString(result);
          postings.put(reference, response);
          respond(exchange, 200, response);
        }
        case "GET" -> {
          String reference = exchange.getRequestURI().getPath().substring("/core/postings/".length());
          String posting = postings.get(reference);
          if (posting == null) {
            respond(exchange, 404, "{\"error\":\"POSTING_NOT_FOUND\"}");
          } else {
            respond(exchange, 200, posting);
          }
        }
        default -> respond(exchange, 405, "{\"error\":\"METHOD_NOT_ALLOWED\"}");
      }
    } finally {
      exchange.close();
    }
  }

  private static void pause(long millis) {
    if (millis <= 0) {
      return;
    }
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private static void respond(HttpExchange exchange, int status, String body) throws IOException {
    if (body == null) {
      exchange.sendResponseHeaders(status, -1);
      return;
    }
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(bytes);
    }
  }
}
