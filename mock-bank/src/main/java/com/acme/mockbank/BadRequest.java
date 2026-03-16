package com.acme.mockbank;

/** Invalid input from the caller. Answered with 400 and the message. */
final class BadRequest extends RuntimeException {

  BadRequest(String message) {
    super(message);
  }
}
