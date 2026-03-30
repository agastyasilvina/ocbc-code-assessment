package com.acme.transfer.config;

import com.acme.core.sdk.CoreBankingClient;
import com.acme.core.sdk.CoreBankingConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CoreBankingConfiguration {

  @Bean
  public CoreBankingClient coreBankingClient(AcmeProperties properties) {
    return CoreBankingClient.create(
        new CoreBankingConfig(properties.coreUrl(), properties.coreReadTimeoutMs()));
  }
}
