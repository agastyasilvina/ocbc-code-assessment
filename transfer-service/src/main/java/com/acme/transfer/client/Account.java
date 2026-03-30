package com.acme.transfer.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Account(String accountNo, String name, String currency, String status,
                      BigDecimal available) {
}
