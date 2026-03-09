package com.acme.core.sdk;

import java.time.Instant;

/**
 * A posting as the core recorded it.
 *
 * @param coreTxnId the core's transaction id; null when the posting was rejected
 * @param reasonCode why the core rejected the posting; null when it was posted
 * @param postedAt when the core recorded the posting
 */
public record PostingResult(String reference, String coreTxnId, Status status,
                            String reasonCode, Instant postedAt) {

  public enum Status {
    POSTED,
    REJECTED
  }
}
