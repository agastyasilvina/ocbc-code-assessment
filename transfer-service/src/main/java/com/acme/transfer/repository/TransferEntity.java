package com.acme.transfer.repository;

import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("transfers")
public record TransferEntity(
    @Id @Column("transfer_id") String transferId,
    @Column("status") String status,
    @Column("reason_code") String reasonCode,
    @Column("source_account") String sourceAccount,
    @Column("destination_account") String destinationAccount,
    @Column("debit_amount") BigDecimal debitAmount,
    @Column("debit_currency") String debitCurrency,
    @Column("credit_amount") BigDecimal creditAmount,
    @Column("credit_currency") String creditCurrency,
    @Column("fx_rate") BigDecimal fxRate,
    @Column("core_txn_id") String coreTxnId,
    @Column("description") String description,
    @Column("created_at") Instant createdAt,
    @Column("updated_at") Instant updatedAt) {
}
