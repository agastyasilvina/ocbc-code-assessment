package com.acme.transfer.job;

import com.acme.core.sdk.CoreBankingClient;
import com.acme.core.sdk.PostingRequest;
import com.acme.core.sdk.PostingResult;
import com.acme.transfer.repository.TransferEntity;
import com.acme.transfer.repository.TransferRepository;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Retries transfers that failed because core banking did not answer in time. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReconciliationJob {

  private final TransferRepository transferRepository;
  private final CoreBankingClient coreBankingClient;
  private final ExecutorService executor = Executors.newFixedThreadPool(50);

  @Scheduled(fixedRate = 30_000, initialDelay = 30_000)
  public void retryTimedOutTransfers() {
    List<TransferEntity> transfers = transferRepository
        .findByStatusAndReasonCode("FAILED", "CORE_TIMEOUT")
        .collectList()
        .block();
    log.info("Reconciliation: {} transfers to retry", transfers.size());
    for (TransferEntity transfer : transfers) {
      executor.submit(() -> retry(transfer));
    }
  }

  private void retry(TransferEntity transfer) {
    try {
      PostingResult result = coreBankingClient.post(new PostingRequest(transfer.transferId(),
          transfer.sourceAccount(), transfer.destinationAccount(), transfer.debitAmount(),
          transfer.debitCurrency(),
          transfer.creditAmount() == null ? transfer.debitAmount() : transfer.creditAmount(),
          transfer.creditCurrency() == null ? transfer.debitCurrency() : transfer.creditCurrency()));
      if (result.status() == PostingResult.Status.POSTED) {
        transferRepository.updateStatus(transfer.transferId(), "COMPLETED", null,
            result.coreTxnId(), Instant.now()).block();
        log.info("Transfer {} completed on retry", transfer.transferId());
      }
    } catch (Exception e) {
      log.warn("Retry of transfer {} failed: {}", transfer.transferId(), e.getMessage());
    }
  }
}
