package com.acme.transfer.repository;

import java.time.Instant;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface AuditEventRepository extends ReactiveCrudRepository<AuditEvent, Long> {

  @Modifying
  @Query("""
      INSERT INTO audit_events (transfer_id, event_type, detail, created_at)
      VALUES (:transferId, :eventType, :detail, :createdAt)
      """)
  Mono<Integer> append(String transferId, String eventType, String detail, Instant createdAt);
}
