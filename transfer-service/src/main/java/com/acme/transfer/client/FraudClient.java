package com.acme.transfer.client;

import com.acme.transfer.config.AcmeProperties;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class FraudClient {

  private final AcmeProperties properties;

  public Mono<FraudDecision> assess(String transferId, String amount, String currency,
                                    String sourceAccount, String destinationAccount) {
    return WebClient.create(properties.fraudUrl())
        .post()
        .uri("/fraud/assessments")
        .bodyValue(Map.of(
            "transferId", transferId,
            "amount", amount,
            "currency", currency,
            "sourceAccount", sourceAccount,
            "destinationAccount", destinationAccount))
        .retrieve()
        .bodyToMono(FraudDecision.class);
  }
}
