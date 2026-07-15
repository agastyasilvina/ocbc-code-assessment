package com.acme.core.sdk;

/** Timeouts applied to every request the SDK sends. */
record SdkTimeouts(int connectMillis, int readMillis) {

  static final int CONNECT_MILLIS = 500;

  static SdkTimeouts from(CoreBankingConfig config) {
    return new SdkTimeouts(CONNECT_MILLIS, config.readTimeoutMs());
  }
}
