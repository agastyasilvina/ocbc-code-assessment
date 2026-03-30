package com.acme.transfer.service;

import com.acme.transfer.repository.AuditEventRepository;
import com.acme.transfer.repository.TransferEntity;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AuditService {

  private final AuditEventRepository auditEventRepository;

  public Mono<Integer> recordTransfer(TransferEntity transfer) {
    return auditEventRepository.append(transfer.transferId(), "TRANSFER_" + transfer.status(),
        transfer.reasonCode(), Instant.now());
  }
}
