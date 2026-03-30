package com.acme.transfer.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record Money(String amount, String currency) {

  public static Money of(BigDecimal amount, String currency) {
    if (amount == null || currency == null) {
      return null;
    }
    return new Money(amount.setScale(2, RoundingMode.HALF_UP).toPlainString(), currency);
  }
}
