package com.acme.transfer.service;

import com.acme.transfer.client.FxClient;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class FxService {

  private final FxClient fxClient;

  public Mono<Conversion> convert(BigDecimal amount, String fromCurrency, String toCurrency) {
    if (fromCurrency.equals(toCurrency)) {
      return Mono.just(new Conversion(amount, toCurrency, null));
    }
    return fxClient.getRate(fromCurrency, toCurrency)
        .map(rate -> {
          double credit = Math.round(amount.doubleValue() * rate.rate().doubleValue() * 100) / 100.0;
          return new Conversion(BigDecimal.valueOf(credit).setScale(2, RoundingMode.HALF_UP),
              toCurrency, rate.rate());
        });
  }
}
