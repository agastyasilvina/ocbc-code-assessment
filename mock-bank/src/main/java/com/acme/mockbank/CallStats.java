package com.acme.mockbank;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Call counts, status counts, concurrency and call times per key for one service. */
final class CallStats {

  private final AtomicLong calls = new AtomicLong();
  private final AtomicInteger inFlight = new AtomicInteger();
  private final AtomicInteger peakConcurrency = new AtomicInteger();
  private final Map<Integer, AtomicLong> statuses = new ConcurrentHashMap<>();
  private final Map<String, Queue<Long>> callsByKey = new ConcurrentHashMap<>();

  /**
   * Counts a call that has just arrived and returns how many calls are now in flight, this one
   * included. Rejected calls count too, so the peak shows what the client attempted.
   */
  int begin(String key, long now) {
    calls.incrementAndGet();
    if (key != null && !key.isEmpty()) {
      callsByKey.computeIfAbsent(key, k -> new ConcurrentLinkedQueue<>()).add(now);
    }
    int current = inFlight.incrementAndGet();
    peakConcurrency.accumulateAndGet(current, Math::max);
    return current;
  }

  void end(int status) {
    inFlight.decrementAndGet();
    statuses.computeIfAbsent(status, s -> new AtomicLong()).incrementAndGet();
  }

  void reset() {
    calls.set(0);
    statuses.clear();
    callsByKey.clear();
    peakConcurrency.set(inFlight.get());
  }

  Map<String, Object> snapshot() {
    Map<String, Long> byStatus = new TreeMap<>();
    statuses.forEach((status, count) -> byStatus.put(Integer.toString(status), count.get()));
    Map<String, List<Long>> byKey = new TreeMap<>();
    callsByKey.forEach((key, times) -> byKey.put(key, List.copyOf(times)));
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("calls", calls.get());
    out.put("inFlight", inFlight.get());
    out.put("peakConcurrency", peakConcurrency.get());
    out.put("statuses", byStatus);
    out.put("callsByKey", byKey);
    return out;
  }
}
