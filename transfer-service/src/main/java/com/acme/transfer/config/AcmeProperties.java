package com.acme.transfer.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "acme")
public record AcmeProperties(String accountsUrl, String fxUrl, String fraudUrl, String coreUrl,
                             int coreReadTimeoutMs, FraudThreshold fraudThreshold) {

  public record FraudThreshold(BigDecimal idr, BigDecimal usd, BigDecimal sgd) {
  }
}
