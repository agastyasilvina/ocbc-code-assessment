package com.acme.transfer.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FraudDecision(String transferId, String decision, double score) {
}
