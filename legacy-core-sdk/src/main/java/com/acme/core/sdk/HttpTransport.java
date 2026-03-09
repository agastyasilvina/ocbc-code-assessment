package com.acme.core.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

/** HTTP calls to the core. One attempt per call: the SDK never retries. */
final class HttpTransport {

  private static final int CONNECT_TIMEOUT_MS = 500;
  private static final String SESSION_HEADER = "X-Core-Session";

  private final String baseUrl;
  private final int readTimeoutMs;
  private final ObjectMapper json = new ObjectMapper();

  HttpTransport(CoreBankingConfig config) {
    this.baseUrl = config.baseUrl();
    this.readTimeoutMs = config.readTimeoutMs();
  }

  String openSession() {
    Response response;
    try {
      response = exchange("POST", "/core/sessions", null, "{}");
    } catch (IOException e) {
      throw new CoreUnavailableException(
          "Could not open a session on " + baseUrl + ": " + e.getMessage(), e);
    }
    if (response.status() == 503) {
      throw new CoreBusyException("No core session available on " + baseUrl);
    }
    if (response.status() != 201) {
      throw unexpected("POST /core/sessions", response);
    }
    String sessionId = parse(response).path("sessionId").asText("");
    if (sessionId.isEmpty()) {
      throw unexpected("POST /core/sessions", response);
    }
    return sessionId;
  }

  void closeSession(String sessionId) throws IOException {
    exchange("DELETE", "/core/sessions/" + encode(sessionId), null, null);
  }

  PostingResult createPosting(String sessionId, PostingRequest request) throws IOException {
    ObjectNode body = json.createObjectNode()
        .put("reference", request.reference())
        .put("debitAccount", request.debitAccount())
        .put("creditAccount", request.creditAccount())
        .put("debitAmount", request.debitAmount().toPlainString())
        .put("debitCurrency", request.debitCurrency())
        .put("creditAmount", request.creditAmount().toPlainString())
        .put("creditCurrency", request.creditCurrency());
    Response response = exchange("POST", "/core/postings", sessionId, json.writeValueAsString(body));
    if (response.status() != 200) {
      throw unexpected("POST /core/postings", response);
    }
    return toResult(parse(response));
  }

  Optional<PostingResult> findPosting(String sessionId, String reference) throws IOException {
    Response response = exchange("GET", "/core/postings/" + encode(reference), sessionId, null);
    if (response.status() == 404) {
      return Optional.empty();
    }
    if (response.status() != 200) {
      throw unexpected("GET /core/postings", response);
    }
    return Optional.of(toResult(parse(response)));
  }

  private Response exchange(String method, String path, String sessionId, String body)
      throws IOException {
    HttpURLConnection connection =
        (HttpURLConnection) URI.create(baseUrl + path).toURL().openConnection();
    connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
    connection.setReadTimeout(readTimeoutMs);
    connection.setRequestMethod(method);
    connection.setRequestProperty("Accept", "application/json");
    if (sessionId != null) {
      connection.setRequestProperty(SESSION_HEADER, sessionId);
    }
    byte[] payload = body == null ? null : body.getBytes(StandardCharsets.UTF_8);
    if (payload != null) {
      connection.setDoOutput(true);
      connection.setFixedLengthStreamingMode(payload.length);
      connection.setRequestProperty("Content-Type", "application/json");
    }
    try {
      connection.connect();
    } catch (IOException e) {
      throw new CoreUnavailableException(
          "Could not connect to core at " + baseUrl + ": " + e.getMessage(), e);
    }
    if (payload != null) {
      try (OutputStream out = connection.getOutputStream()) {
        out.write(payload);
      }
    }
    int status = connection.getResponseCode();
    InputStream in = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
    if (in == null) {
      return new Response(status, new byte[0]);
    }
    try (in) {
      return new Response(status, in.readAllBytes());
    }
  }

  private JsonNode parse(Response response) {
    try {
      return json.readTree(response.body());
    } catch (IOException e) {
      throw new CoreBankingException("Unreadable response from core at " + baseUrl, e);
    }
  }

  private PostingResult toResult(JsonNode node) {
    try {
      String postedAt = node.path("postedAt").asText(null);
      return new PostingResult(
          node.path("reference").asText(null),
          node.path("coreTxnId").asText(null),
          PostingResult.Status.valueOf(node.path("status").asText("")),
          node.path("reasonCode").asText(null),
          postedAt == null ? null : Instant.parse(postedAt));
    } catch (RuntimeException e) {
      throw new CoreBankingException("Unexpected posting from core at " + baseUrl + ": " + node, e);
    }
  }

  private CoreBankingException unexpected(String operation, Response response) {
    return new CoreBankingException(
        operation + " on " + baseUrl + " returned HTTP " + response.status() + ": "
            + new String(response.body(), StandardCharsets.UTF_8));
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }

  private record Response(int status, byte[] body) {
  }
}
