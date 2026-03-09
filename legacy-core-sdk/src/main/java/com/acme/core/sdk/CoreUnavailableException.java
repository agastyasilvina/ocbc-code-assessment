package com.acme.core.sdk;

/** The core could not be reached. Nothing was sent; it is safe to retry later. */
public class CoreUnavailableException extends CoreBankingException {

  public CoreUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
