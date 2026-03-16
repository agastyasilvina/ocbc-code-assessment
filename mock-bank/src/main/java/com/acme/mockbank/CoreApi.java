package com.acme.mockbank;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The core banking system. Only the SDK talks to it.
 *
 * <ul>
 *   <li>{@code POST /core/sessions}: at most 10 open sessions, otherwise 503 BUSY. Sessions that
 *       are never closed expire after 120 seconds.
 *   <li>{@code DELETE /core/sessions/{id}}
 *   <li>{@code POST /core/postings}: needs an open session. Postings are never deduplicated.
 *   <li>{@code GET /core/postings/{reference}}: needs an open session. References are
 *       case-sensitive.
 * </ul>
 */
final class CoreApi implements Service {

  private static final String SESSION_HEADER = "X-Core-Session";

  private final Settings settings;
  private final CallStats stats = new CallStats();

  private final ReentrantLock sessionLock = new ReentrantLock();
  private final Map<String, Long> openSessions = new HashMap<>();
  private long sessionSeq;
  private long sessionsOpened;
  private long sessionsClosed;
  private long busyRejections;
  private long expiredWithoutClose;
  private int peakOpenSessions;

  private final AtomicLong txnSeq = new AtomicLong();
  private final Map<String, Queue<PostingRecord>> postings = new ConcurrentHashMap<>();
  private final Map<String, AtomicInteger> attempts = new ConcurrentHashMap<>();
  private final Queue<Map<String, Object>> hung = new ConcurrentLinkedQueue<>();
  private final Map<String, AtomicInteger> inquiries = new ConcurrentHashMap<>();

  CoreApi(Settings settings) {
    this.settings = settings;
  }

  @Override
  public String name() {
    return "core";
  }

  void handle(HttpExchange exchange) throws Exception {
    String method = exchange.getRequestMethod();
    String path = exchange.getRequestURI().getPath();
    String operation;
    if (method.equals("POST") && path.equals("/core/sessions")) {
      operation = "openSession";
    } else if (method.equals("DELETE") && path.startsWith("/core/sessions/")) {
      operation = "closeSession";
    } else if (method.equals("POST") && path.equals("/core/postings")) {
      operation = "post";
    } else if (method.equals("GET") && path.startsWith("/core/postings/")) {
      operation = "inquire";
    } else {
      Http.json(exchange, 404, Http.error("NOT_FOUND"));
      return;
    }
    stats.begin(operation, settings.now());
    int status = 500;
    try {
      status = switch (operation) {
        case "openSession" -> openSession(exchange);
        case "closeSession" -> closeSession(exchange);
        case "post" -> post(exchange);
        default -> inquire(exchange);
      };
    } catch (BadRequest e) {
      status = 400;
      throw e;
    } finally {
      stats.end(status);
    }
  }

  private int openSession(HttpExchange exchange) throws Exception {
    Http.body(exchange);
    Chaos.Core chaos = settings.chaos().core;
    long now = settings.now();
    String id = null;
    sessionLock.lock();
    try {
      purgeExpired(now);
      if (openSessions.size() >= chaos.maxSessions) {
        busyRejections++;
      } else {
        id = "S" + (++sessionSeq);
        openSessions.put(id, now);
        sessionsOpened++;
        peakOpenSessions = Math.max(peakOpenSessions, openSessions.size());
      }
    } finally {
      sessionLock.unlock();
    }
    if (id == null) {
      Http.json(exchange, 503, Http.error("BUSY"));
      return 503;
    }
    Http.json(exchange, 201, Map.of("sessionId", id));
    return 201;
  }

  private int closeSession(HttpExchange exchange) throws Exception {
    String id = Http.pathAfter(exchange, "/core/sessions/");
    boolean closed;
    sessionLock.lock();
    try {
      purgeExpired(settings.now());
      closed = openSessions.remove(id) != null;
      if (closed) {
        sessionsClosed++;
      }
    } finally {
      sessionLock.unlock();
    }
    if (!closed) {
      Http.json(exchange, 404, Http.error("SESSION_NOT_FOUND"));
      return 404;
    }
    Http.noContent(exchange);
    return 204;
  }

  private int post(HttpExchange exchange) throws Exception {
    long startedAt = settings.now();
    if (!sessionIsOpen(exchange)) {
      Http.json(exchange, 401, Http.error("INVALID_SESSION"));
      return 401;
    }
    PostingInput input = PostingInput.from(Http.readJson(exchange));
    attempts.computeIfAbsent(input.reference(), r -> new AtomicInteger()).incrementAndGet();
    Chaos.Core chaos = settings.chaos().core;
    OverrideState overrides = settings.overrides();

    Boolean scriptedHang = overrides.nextCoreHang();
    boolean hangs = scriptedHang != null || settings.chance(chaos.hangRate);
    if (!hangs) {
      Http.sleep(settings.pick(chaos.latencyMs));
      PostingRecord stored = store(input, startedAt, false);
      stored.endedAt = settings.now();
      Http.json(exchange, 200, stored.result());
      return 200;
    }

    // Hung posting: the response takes hangMs. When applied, the posting exists from the start,
    // so an inquiry finds it while the caller is still waiting or has given up.
    boolean applied = scriptedHang != null ? scriptedHang : settings.chance(chaos.applyOnHangRate);
    int hangMs = overrides.coreHangMs() != null ? overrides.coreHangMs() : chaos.hangMs;
    PostingRecord stored = applied ? store(input, startedAt, true) : null;
    Map<String, Object> groundTruth = new LinkedHashMap<>();
    groundTruth.put("reference", input.reference());
    groundTruth.put("debitAccount", input.debitAccount());
    groundTruth.put("applied", applied);
    groundTruth.put("startedAt", startedAt);
    hung.add(groundTruth);
    Http.sleep(hangMs);
    if (stored == null) {
      Http.json(exchange, 504, Http.error("CORE_TIMEOUT"));
      return 504;
    }
    stored.endedAt = settings.now();
    Http.json(exchange, 200, stored.result());
    return 200;
  }

  private int inquire(HttpExchange exchange) throws Exception {
    if (!sessionIsOpen(exchange)) {
      Http.json(exchange, 401, Http.error("INVALID_SESSION"));
      return 401;
    }
    String reference = Http.pathAfter(exchange, "/core/postings/");
    inquiries.computeIfAbsent(reference, r -> new AtomicInteger()).incrementAndGet();
    Http.sleep(settings.pick(settings.chaos().core.inquiryLatencyMs));
    Queue<PostingRecord> records = postings.get(reference);
    Optional<PostingRecord> found = records == null
        ? Optional.empty()
        : records.stream().filter(PostingRecord::posted).findFirst()
            .or(() -> Optional.ofNullable(records.peek()));
    if (found.isEmpty()) {
      Http.json(exchange, 404, Http.error("POSTING_NOT_FOUND"));
      return 404;
    }
    Http.json(exchange, 200, found.get().result());
    return 200;
  }

  private boolean sessionIsOpen(HttpExchange exchange) {
    String id = exchange.getRequestHeaders().getFirst(SESSION_HEADER);
    if (id == null) {
      return false;
    }
    sessionLock.lock();
    try {
      purgeExpired(settings.now());
      return openSessions.containsKey(id);
    } finally {
      sessionLock.unlock();
    }
  }

  /** Call with {@code sessionLock} held. */
  private void purgeExpired(long now) {
    long ttlMillis = settings.chaos().core.sessionTtlSeconds * 1000L;
    Iterator<Map.Entry<String, Long>> it = openSessions.entrySet().iterator();
    while (it.hasNext()) {
      if (now - it.next().getValue() >= ttlMillis) {
        it.remove();
        expiredWithoutClose++;
      }
    }
  }

  private PostingRecord store(PostingInput input, long startedAt, boolean hangs) {
    Outcome outcome = evaluate(input);
    String coreTxnId = outcome.posted() ? String.format("CT%08d", txnSeq.incrementAndGet()) : null;
    PostingRecord stored = new PostingRecord(input, coreTxnId, outcome, startedAt, hangs,
        settings.instant());
    postings.computeIfAbsent(input.reference(), r -> new ConcurrentLinkedQueue<>()).add(stored);
    return stored;
  }

  private static Outcome evaluate(PostingInput input) {
    Optional<Fixtures.Account> debit = Fixtures.account(input.debitAccount());
    Optional<Fixtures.Account> credit = Fixtures.account(input.creditAccount());
    if (debit.isEmpty() || credit.isEmpty()) {
      return Outcome.rejected("ACCOUNT_NOT_FOUND");
    }
    if (!debit.get().active() || !credit.get().active()) {
      return Outcome.rejected("ACCOUNT_NOT_ACTIVE");
    }
    if (!debit.get().currency().equals(input.debitCurrency())
        || !credit.get().currency().equals(input.creditCurrency())) {
      return Outcome.rejected("CURRENCY_MISMATCH");
    }
    if (input.debitAmount().signum() <= 0 || input.creditAmount().signum() <= 0) {
      return Outcome.rejected("INVALID_AMOUNT");
    }
    if (input.debitAmount().compareTo(debit.get().available()) > 0) {
      return Outcome.rejected("INSUFFICIENT_FUNDS");
    }
    return new Outcome("POSTED", null);
  }

  @Override
  public void reset() {
    stats.reset();
    sessionLock.lock();
    try {
      openSessions.clear();
      sessionsOpened = 0;
      sessionsClosed = 0;
      busyRejections = 0;
      expiredWithoutClose = 0;
      peakOpenSessions = 0;
    } finally {
      sessionLock.unlock();
    }
    postings.clear();
    attempts.clear();
    hung.clear();
    inquiries.clear();
  }

  @Override
  public Map<String, Object> stats() {
    Map<String, Object> out = stats.snapshot();

    Map<String, Object> sessions = new LinkedHashMap<>();
    sessionLock.lock();
    try {
      purgeExpired(settings.now());
      sessions.put("open", openSessions.size());
      sessions.put("opened", sessionsOpened);
      sessions.put("closed", sessionsClosed);
      sessions.put("peakOpen", peakOpenSessions);
      sessions.put("busyRejections", busyRejections);
      sessions.put("expiredWithoutClose", expiredWithoutClose);
    } finally {
      sessionLock.unlock();
    }
    out.put("sessions", sessions);

    Map<String, List<Map<String, Object>>> byReference = new TreeMap<>();
    List<String> duplicates = new ArrayList<>();
    int total = 0;
    for (Map.Entry<String, Queue<PostingRecord>> entry : postings.entrySet()) {
      List<PostingRecord> records = List.copyOf(entry.getValue());
      byReference.put(entry.getKey(), records.stream().map(PostingRecord::stats).toList());
      total += records.size();
      if (records.stream().filter(PostingRecord::posted).count() > 1) {
        duplicates.add(entry.getKey());
      }
    }
    duplicates.sort(null);
    Map<String, Object> postingStats = new LinkedHashMap<>();
    postingStats.put("total", total);
    postingStats.put("byReference", byReference);
    out.put("postings", postingStats);
    out.put("duplicateReferences", duplicates);

    List<String> repeated = new ArrayList<>();
    attempts.forEach((reference, count) -> {
      if (count.get() > 1) {
        repeated.add(reference);
      }
    });
    repeated.sort(null);
    out.put("repeatedReferences", repeated);
    out.put("hung", List.copyOf(hung));

    Map<String, Integer> inquiriesByReference = new TreeMap<>();
    inquiries.forEach((reference, count) -> inquiriesByReference.put(reference, count.get()));
    out.put("inquiries", inquiriesByReference);
    return out;
  }

  private record PostingInput(String reference, String debitAccount, String creditAccount,
                              BigDecimal debitAmount, String debitCurrency,
                              BigDecimal creditAmount, String creditCurrency) {

    static PostingInput from(JsonNode body) {
      return new PostingInput(
          required(body, "reference"),
          required(body, "debitAccount"),
          required(body, "creditAccount"),
          amount(body, "debitAmount"),
          required(body, "debitCurrency"),
          amount(body, "creditAmount"),
          required(body, "creditCurrency"));
    }

    private static String required(JsonNode body, String field) {
      String value = Http.text(body, field);
      if (value == null || value.isBlank()) {
        throw new BadRequest(field + " is required");
      }
      return value;
    }

    private static BigDecimal amount(JsonNode body, String field) {
      String value = required(body, field);
      try {
        return new BigDecimal(value);
      } catch (NumberFormatException e) {
        throw new BadRequest(field + " must be a decimal string");
      }
    }
  }

  private record Outcome(String status, String reasonCode) {

    static Outcome rejected(String reasonCode) {
      return new Outcome("REJECTED", reasonCode);
    }

    boolean posted() {
      return "POSTED".equals(status);
    }
  }

  private static final class PostingRecord {

    private final PostingInput input;
    private final String coreTxnId;
    private final Outcome outcome;
    private final long startedAt;
    private final boolean hung;
    private final Instant postedAt;
    private volatile long endedAt = -1;

    PostingRecord(PostingInput input, String coreTxnId, Outcome outcome, long startedAt,
                  boolean hung, Instant postedAt) {
      this.input = input;
      this.coreTxnId = coreTxnId;
      this.outcome = outcome;
      this.startedAt = startedAt;
      this.hung = hung;
      this.postedAt = postedAt;
    }

    boolean posted() {
      return outcome.posted();
    }

    Map<String, Object> result() {
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("reference", input.reference());
      body.put("coreTxnId", coreTxnId);
      body.put("status", outcome.status());
      body.put("reasonCode", outcome.reasonCode());
      body.put("postedAt", postedAt.toString());
      return body;
    }

    Map<String, Object> stats() {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("coreTxnId", coreTxnId);
      row.put("status", outcome.status());
      row.put("reasonCode", outcome.reasonCode());
      row.put("debitAccount", input.debitAccount());
      row.put("creditAccount", input.creditAccount());
      row.put("debitAmount", input.debitAmount().toPlainString());
      row.put("debitCurrency", input.debitCurrency());
      row.put("creditAmount", input.creditAmount().toPlainString());
      row.put("creditCurrency", input.creditCurrency());
      row.put("startedAt", startedAt);
      row.put("endedAt", endedAt < 0 ? null : endedAt);
      row.put("hung", hung);
      return row;
    }
  }
}
