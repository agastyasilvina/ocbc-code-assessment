package com.acme.core.sdk;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * One posting: debit one account, credit another.
 *
 * @param reference your reference for the posting; the core keeps it exactly as given
 */
public record PostingRequest(String reference, String debitAccount, String creditAccount,
                             BigDecimal debitAmount, String debitCurrency,
                             BigDecimal creditAmount, String creditCurrency) {

  public PostingRequest {
    Objects.requireNonNull(reference, "reference");
    Objects.requireNonNull(debitAccount, "debitAccount");
    Objects.requireNonNull(creditAccount, "creditAccount");
    Objects.requireNonNull(debitAmount, "debitAmount");
    Objects.requireNonNull(debitCurrency, "debitCurrency");
    Objects.requireNonNull(creditAmount, "creditAmount");
    Objects.requireNonNull(creditCurrency, "creditCurrency");
    if (reference.isBlank()) {
      throw new IllegalArgumentException("reference must not be blank");
    }
  }
}
