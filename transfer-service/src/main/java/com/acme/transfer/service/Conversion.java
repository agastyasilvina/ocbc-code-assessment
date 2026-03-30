package com.acme.transfer.service;

import java.math.BigDecimal;

/** The credit side of a transfer. {@code rate} is null when no conversion was needed. */
public record Conversion(BigDecimal creditAmount, String creditCurrency, BigDecimal rate) {
}
