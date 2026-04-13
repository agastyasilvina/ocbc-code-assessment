package com.acme.transfer.client;

import com.acme.transfer.config.AcmeProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class FxClient {

  private final AcmeProperties properties;

  public Mono<FxRate> getRate(String base, String quote) {
    return WebClient.create(properties.fxUrl())
        .get()
        .uri(uri -> uri.path("/fx/rates").queryParam("base", base).queryParam("quote", quote).build())
        .retrieve()
        .bodyToMono(FxRate.class)
        .retry(3);
  }
}
