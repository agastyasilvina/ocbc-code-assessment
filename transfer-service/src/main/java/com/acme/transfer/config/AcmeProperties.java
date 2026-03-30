package com.acme.transfer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "acme")
public record AcmeProperties(String accountsUrl, String coreUrl, int coreReadTimeoutMs) {
}
