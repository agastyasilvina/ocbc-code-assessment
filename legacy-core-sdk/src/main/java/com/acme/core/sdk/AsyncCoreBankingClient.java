package com.acme.core.sdk;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Asynchronous wrapper around {@link CoreBankingClient}.
 *
 * <p>Work in progress: not reviewed and not load tested. Do not use.
 */
public final class AsyncCoreBankingClient implements AutoCloseable {

  private final CoreBankingClient client;
  private final ExecutorService executor = Executors.newFixedThreadPool(32);

  public AsyncCoreBankingClient(CoreBankingClient client) {
    this.client = client;
  }

  public CompletableFuture<PostingResult> post(PostingRequest request) {
    return CompletableFuture.supplyAsync(() -> client.post(request), executor);
  }

  public CompletableFuture<Optional<PostingResult>> inquire(String reference) {
    return CompletableFuture.supplyAsync(() -> client.inquire(reference), executor);
  }

  @Override
  public void close() {
    executor.shutdown();
  }
}
