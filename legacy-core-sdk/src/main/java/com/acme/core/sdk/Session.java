package com.acme.core.sdk;

import java.io.IOException;

/** One core session. Not thread-safe on its own; {@link CoreBankingClient} guards it. */
final class Session {

  private static final System.Logger LOG = System.getLogger(Session.class.getName());

  private final HttpTransport transport;
  private String id;

  Session(HttpTransport transport) {
    this.transport = transport;
  }

  void open() {
    id = transport.openSession();
  }

  String id() {
    if (id == null) {
      throw new IllegalStateException("session is not open");
    }
    return id;
  }

  /** Closes the session. A failure to close is logged, never thrown. */
  void close() {
    if (id == null) {
      return;
    }
    String closing = id;
    id = null;
    try {
      transport.closeSession(closing);
    } catch (IOException | RuntimeException e) {
      LOG.log(System.Logger.Level.WARNING, "Could not close core session {0}: {1}",
          closing, e.getMessage());
    }
  }
}
