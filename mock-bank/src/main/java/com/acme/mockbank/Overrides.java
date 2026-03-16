package com.acme.mockbank;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic scripts, set through {@code PUT /__admin/overrides}. Public fields so that Jackson
 * can read and write them.
 */
public final class Overrides {

  /** Scripts per account number. */
  public Map<String, AccountScript> accounts = new LinkedHashMap<>();
  public FxScript fx = new FxScript();
  public FraudScript fraud = new FraudScript();
  public CoreScript core = new CoreScript();

  /** One scripted answer. Status 200 means "answer normally". */
  public static final class Response {
    public int status = 200;
    /** Seconds for the {@code Retry-After} header. */
    public Integer retryAfter;
    /** Replaces the chaos latency for this answer. */
    public Integer latencyMs;
    /** With status 200: send a truncated, unparseable body. */
    public boolean malformed;
  }

  public static final class AccountScript {
    /** Used one per call, in order. */
    public List<Response> responses = new ArrayList<>();
    /** Used for every call once {@code responses} is used up. */
    public Response then;
  }

  public static final class FxScript {
    /** Fixed rates by pair, for example {@code "USD/IDR": "16000.00000000"}. */
    public Map<String, String> fixedRates = new LinkedHashMap<>();
    /** The next N rate calls fail with {@code failStatus}. */
    public int failNext;
    public int failStatus = 500;
    public Integer retryAfter;
  }

  public static final class FraudScript {
    /** Behaviour by amount, for example {@code "7000.00"}. */
    public Map<String, FraudBehaviour> byAmount = new LinkedHashMap<>();
  }

  public static final class FraudBehaviour {
    /** Wait this long before answering. */
    public Integer hangMs;
    /** Fail with this status instead of deciding. */
    public Integer status;
    /** Answer with this decision: ALLOW, DENY or REVIEW. */
    public String decision;
  }

  public static final class CoreScript {
    /** The next N postings hang. */
    public int hangNext;
    /** Which hung postings are applied: all, none or alternate (first applied). */
    public String applied = "alternate";
    /** Replaces the chaos hang duration. */
    public Integer hangMs;
  }
}
