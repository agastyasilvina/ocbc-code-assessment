package com.acme.transfer.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface IdempotencyRepository extends ReactiveCrudRepository<IdempotencyEntity, String> {

  Flux<IdempotencyEntity> findByIdempotencyKey(String idempotencyKey);
}
