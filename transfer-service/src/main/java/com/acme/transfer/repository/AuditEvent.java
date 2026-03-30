package com.acme.transfer.repository;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("audit_events")
public record AuditEvent(
    @Id Long id,
    @Column("transfer_id") String transferId,
    @Column("event_type") String eventType,
    @Column("detail") String detail,
    @Column("created_at") Instant createdAt) {
}
