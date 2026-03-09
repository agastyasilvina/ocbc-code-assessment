package com.acme.core.sdk;

/** No core session was available. Nothing was sent; it is safe to retry later. */
public class CoreBusyException extends CoreBankingException {

  public CoreBusyException(String message) {
    super(message);
  }
}
