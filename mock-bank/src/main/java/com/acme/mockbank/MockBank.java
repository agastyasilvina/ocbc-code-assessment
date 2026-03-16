package com.acme.mockbank;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Stand-ins for the systems the transfer service integrates with: accounts, FX, fraud and core
 * banking, plus an admin API to inject failures and read what happened.
 */
public final class MockBank implements AutoCloseable {

  private final HttpServer server;
  private final ExecutorService executor;

  private MockBank(HttpServer server, ExecutorService executor) {
    this.server = server;
    this.executor = executor;
  }

  /** Starts on {@code port} (0 picks a free one). {@code baseline} is what a reset restores. */
  public static MockBank start(int port, Clock clock, Chaos baseline) throws IOException {
    Settings settings = new Settings(clock, baseline);
    AccountsApi accounts = new AccountsApi(settings);
    FxApi fx = new FxApi(settings);
    FraudApi fraud = new FraudApi(settings);
    CoreApi core = new CoreApi(settings);
    AdminApi admin = new AdminApi(settings, List.of(accounts, fx, fraud, core));

    HttpServer server = HttpServer.create(new InetSocketAddress(port), 512);
    ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    server.setExecutor(executor);
    server.createContext("/accounts/", Http.handler(accounts::handle));
    server.createContext("/fx/rates", Http.handler(fx::handle));
    server.createContext("/fraud/assessments", Http.handler(fraud::handle));
    server.createContext("/core/", Http.handler(core::handle));
    server.createContext("/__admin/", Http.handler(admin::handle));
    server.createContext("/health",
        Http.handler(exchange -> Http.json(exchange, 200, Map.of("status", "UP"))));
    server.start();
    return new MockBank(server, executor);
  }

  public int port() {
    return server.getAddress().getPort();
  }

  @Override
  public void close() {
    server.stop(0);
    executor.shutdownNow();
  }

  public static void main(String[] args) throws IOException {
    int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "9090"));
    MockBank bank = start(port, Clock.systemUTC(), Chaos.defaults());
    Runtime.getRuntime().addShutdownHook(new Thread(bank::close));
    System.out.println("Mock bank listening on port " + bank.port());
  }
}
