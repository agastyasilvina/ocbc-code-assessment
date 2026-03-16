package com.acme.mockbank;

import java.util.Map;

/** A simulated downstream service with its own statistics. */
interface Service {

  /** The key of this service in {@code GET /__admin/stats}. */
  String name();

  /** Clears state and statistics. */
  void reset();

  Map<String, Object> stats();
}
