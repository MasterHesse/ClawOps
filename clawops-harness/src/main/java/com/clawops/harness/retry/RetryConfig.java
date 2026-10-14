package com.clawops.harness.retry;

/**
 * 重试策略配置。
 *
 * @param maxAttempts 最大尝试次数（含首次，默认 3）
 * @param baseDelayMillis 首次重试等待基数（默认 500ms）
 * @param maxDelayMillis 最大重试等待（默认 5000ms）
 * @param timeoutMillis 单次调用超时（默认 5000ms）
 */
public record RetryConfig(
    int maxAttempts, long baseDelayMillis, long maxDelayMillis, long timeoutMillis) {

  public static final int DEFAULT_MAX_ATTEMPTS = 3;
  public static final long DEFAULT_BASE_DELAY_MS = 500;
  public static final long DEFAULT_MAX_DELAY_MS = 5000;
  public static final long DEFAULT_TIMEOUT_MS = 5000;

  public RetryConfig {
    if (maxAttempts < 1) {
      throw new IllegalArgumentException("maxAttempts must be >= 1");
    }
    if (baseDelayMillis < 0) {
      throw new IllegalArgumentException("baseDelayMillis must be >= 0");
    }
    if (maxDelayMillis < baseDelayMillis) {
      maxDelayMillis = baseDelayMillis;
    }
    if (timeoutMillis < 1) {
      throw new IllegalArgumentException("timeoutMillis must be >= 1");
    }
  }

  public RetryConfig() {
    this(DEFAULT_MAX_ATTEMPTS, DEFAULT_BASE_DELAY_MS, DEFAULT_MAX_DELAY_MS, DEFAULT_TIMEOUT_MS);
  }

  public long computeDelay(int attempt) {
    if (attempt <= 1) {
      return 0;
    }
    long delay = baseDelayMillis * (1L << (attempt - 2));
    return Math.min(delay, maxDelayMillis);
  }
}
