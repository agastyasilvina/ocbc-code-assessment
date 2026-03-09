package com.acme.core.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class CoreBankingConfigTest {

  @Test
  void readTimeoutDefaultsToTwoSeconds() {
    CoreBankingConfig config = CoreBankingConfig.fromEnv(Map.of("CORE_BASE_URL", "http://core:9090"));

    assertEquals("http://core:9090", config.baseUrl());
    assertEquals(2000, config.readTimeoutMs());
  }

  @Test
  void readsReadTimeoutFromEnvironment() {
    CoreBankingConfig config = CoreBankingConfig.fromEnv(
        Map.of("CORE_BASE_URL", "http://core:9090", "CORE_SDK_READ_TIMEOUT_MS", "750"));

    assertEquals(750, config.readTimeoutMs());
  }

  @Test
  void stripsTrailingSlash() {
    assertEquals("http://core:9090", new CoreBankingConfig("http://core:9090/", 2000).baseUrl());
  }

  @Test
  void requiresBaseUrl() {
    assertThrows(IllegalStateException.class, () -> CoreBankingConfig.fromEnv(Map.of()));
  }

  @Test
  void rejectsNonPositiveReadTimeout() {
    assertThrows(IllegalArgumentException.class, () -> new CoreBankingConfig("http://core:9090", 0));
  }
}
