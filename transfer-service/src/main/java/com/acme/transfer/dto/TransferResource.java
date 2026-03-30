package com.acme.transfer.dto;

import com.acme.transfer.repository.TransferEntity;
import java.time.Instant;

public record TransferResource(String transferId, String status, String reasonCode,
                               String sourceAccount, String destinationAccount, Money debit,
                               Money credit, String fxRate, String coreTxnId, Instant createdAt,
                               Instant updatedAt) {

  public static TransferResource from(TransferEntity transfer) {
    return new TransferResource(
        transfer.transferId(),
        transfer.status(),
        transfer.reasonCode(),
        transfer.sourceAccount(),
        transfer.destinationAccount(),
        Money.of(transfer.debitAmount(), transfer.debitCurrency()),
        Money.of(transfer.creditAmount(), transfer.creditCurrency()),
        transfer.fxRate() == null ? null : transfer.fxRate().stripTrailingZeros().toPlainString(),
        transfer.coreTxnId(),
        transfer.createdAt(),
        transfer.updatedAt());
  }
}
