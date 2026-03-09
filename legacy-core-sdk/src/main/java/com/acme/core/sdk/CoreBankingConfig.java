package com.acme.core.sdk;

import java.util.Map;
import java.util.Objects;

/**
 * Connection settings for {@link CoreBankingClient}.
 *
 * @param baseUrl base URL of the core, for example {@code http://core.example.com:9090}
 * @param readTimeoutMs how long to wait for a response once a request has been sent
 */
public record CoreBankingConfig(String baseUrl, int readTimeoutMs) {

  public static final int DEFAULT_READ_TIMEOUT_MS = 2000;

  public CoreBankingConfig {
    Objects.requireNonNull(baseUrl, "baseUrl");
    if (baseUrl.isBlank()) {
      throw new IllegalArgumentException("baseUrl must not be blank");
    }
    if (readTimeoutMs <= 0) {
      throw new IllegalArgumentException("readTimeoutMs must be positive");
    }
    baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
  }

  /** Reads {@code CORE_BASE_URL} (required) and {@code CORE_SDK_READ_TIMEOUT_MS} (optional). */
  public static CoreBankingConfig fromEnv() {
    return fromEnv(System.getenv());
  }

  static CoreBankingConfig fromEnv(Map<String, String> env) {
    String baseUrl = env.get("CORE_BASE_URL");
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new IllegalStateException("CORE_BASE_URL is not set");
    }
    String readTimeout = env.get("CORE_SDK_READ_TIMEOUT_MS");
    int readTimeoutMs = readTimeout == null || readTimeout.isBlank()
        ? DEFAULT_READ_TIMEOUT_MS
        : Integer.parseInt(readTimeout.trim());
    return new CoreBankingConfig(baseUrl.trim(), readTimeoutMs);
  }
}
