package com.acme.transfer.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FxRate(String base, String quote, BigDecimal rate, Instant validUntil) {
}
