package com.acme.core.sdk;

/** Base class of every exception the SDK throws. */
public class CoreBankingException extends RuntimeException {

  public CoreBankingException(String message) {
    super(message);
  }

  public CoreBankingException(String message, Throwable cause) {
    super(message, cause);
  }
}
