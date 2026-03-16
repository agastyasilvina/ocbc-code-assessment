package com.acme.mockbank;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Deterministic accounts and exchange rates.
 *
 * <p>Account numbers have 10 digits. The first digit is the currency (1 IDR, 2 USD, 3 SGD). A
 * last digit of 7 is DORMANT, 8 is BLOCKED and 9 does not exist. Numbers ending in 11 have 10.00
 * available; every other account has IDR 1,000,000,000.00, USD 100,000.00 or SGD 100,000.00.
 * Balances never change.
 */
final class Fixtures {

  static final Set<String> CURRENCIES = Set.of("IDR", "USD", "SGD");

  private static final Map<String, BigDecimal> BASE_RATES = Map.of(
      "USD/IDR", new BigDecimal("16250.5037"),
      "SGD/IDR", new BigDecimal("12100.2519"),
      "USD/SGD", new BigDecimal("1.3431"));

  private Fixtures() {
  }

  record Account(String accountNo, String name, String currency, String status,
                 BigDecimal available) {

    boolean active() {
      return "ACTIVE".equals(status);
    }
  }

  static Optional<Account> account(String accountNo) {
    if (accountNo == null || !accountNo.matches("\\d{10}")) {
      return Optional.empty();
    }
    String currency = switch (accountNo.charAt(0)) {
      case '1' -> "IDR";
      case '2' -> "USD";
      case '3' -> "SGD";
      default -> null;
    };
    char last = accountNo.charAt(9);
    if (currency == null || last == '9') {
      return Optional.empty();
    }
    String status = switch (last) {
      case '7' -> "DORMANT";
      case '8' -> "BLOCKED";
      default -> "ACTIVE";
    };
    BigDecimal available;
    if (accountNo.endsWith("11")) {
      available = new BigDecimal("10.00");
    } else if (currency.equals("IDR")) {
      available = new BigDecimal("1000000000.00");
    } else {
      available = new BigDecimal("100000.00");
    }
    return Optional.of(new Account(accountNo, "ACME Customer " + accountNo.substring(6), currency,
        status, available));
  }

  /** The base rate from {@code base} to {@code quote}; inverse pairs are rounded to 8 places. */
  static Optional<BigDecimal> rate(String base, String quote) {
    BigDecimal direct = BASE_RATES.get(base + "/" + quote);
    if (direct != null) {
      return Optional.of(direct);
    }
    BigDecimal inverse = BASE_RATES.get(quote + "/" + base);
    if (inverse != null) {
      return Optional.of(BigDecimal.ONE.divide(inverse, 8, RoundingMode.HALF_EVEN));
    }
    return Optional.empty();
  }
}
