package com.acme.transfer.dto;

public record TransferRequest(String sourceAccount, String destinationAccount, String amount,
                              String currency, String description) {
}
