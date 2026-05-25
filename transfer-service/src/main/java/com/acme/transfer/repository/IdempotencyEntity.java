package com.acme.transfer.repository;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("idempotency_keys")
public record IdempotencyEntity(
    @Id @Column("idempotency_key") String idempotencyKey,
    @Column("request_hash") String requestHash,
    @Column("transfer_id") String transferId,
    @Column("response_status") Integer responseStatus,
    @Column("response_body") String responseBody,
    @Column("created_at") Instant createdAt) {
}
