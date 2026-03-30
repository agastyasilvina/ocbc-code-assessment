package com.acme.transfer.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface TransferRepository extends ReactiveCrudRepository<TransferEntity, String> {
}
