package com.acme.transfer.client;

import com.acme.transfer.config.AcmeProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AccountClient {

  private final AcmeProperties properties;

  /** Returns empty when the account does not exist. */
  public Mono<Account> getAccount(String accountNo) {
    return WebClient.create(properties.accountsUrl())
        .get()
        .uri("/accounts/{accountNo}", accountNo)
        .retrieve()
        .bodyToMono(Account.class)
        .retry(3)
        .onErrorResume(WebClientResponseException.NotFound.class, e -> Mono.empty());
  }
}
