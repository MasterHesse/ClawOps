package com.clawops.harness.retry;

import java.time.Duration;
import java.time.Instant;

/**
 * 简单熔断器。
 *
 * <p>状态：CLOSED（正常）→ OPEN（连续失败阈值后）→ HALF_OPEN（等待一段时间后）→ CLOSED（探测成功）。
 */
public class CircuitBreaker {

  public enum State {
    CLOSED,
    OPEN,
    HALF_OPEN
  }

  private final int failureThreshold;
  private final Duration openDuration;
  private final int halfOpenMaxCalls;

  private State state = State.CLOSED;
  private int consecutiveFailures = 0;
  private int halfOpenCalls = 0;
  private Instant openedAt;

  public CircuitBreaker() {
    this(5, Duration.ofSeconds(10), 1);
  }

  public CircuitBreaker(int failureThreshold, Duration openDuration, int halfOpenMaxCalls) {
    this.failureThreshold = failureThreshold;
    this.openDuration = openDuration;
    this.halfOpenMaxCalls = halfOpenMaxCalls;
  }

  public synchronized State state() {
    if (state == State.OPEN && openedAt.plus(openDuration).isBefore(Instant.now())) {
      state = State.HALF_OPEN;
      halfOpenCalls = 0;
    }
    return state;
  }

  public synchronized boolean allow() {
    State current = state();
    return switch (current) {
      case CLOSED -> true;
      case OPEN -> false;
      case HALF_OPEN -> halfOpenCalls < halfOpenMaxCalls;
    };
  }

  public synchronized void recordSuccess() {
    consecutiveFailures = 0;
    halfOpenCalls = 0;
    if (state == State.HALF_OPEN) {
      state = State.CLOSED;
    }
  }

  public synchronized void recordFailure() {
    consecutiveFailures++;
    if (state == State.HALF_OPEN) {
      state = State.OPEN;
      openedAt = Instant.now();
      halfOpenCalls = 0;
    } else if (consecutiveFailures >= failureThreshold) {
      state = State.OPEN;
      openedAt = Instant.now();
    }
    if (state == State.HALF_OPEN) {
      halfOpenCalls++;
    }
  }

  public synchronized void recordAttempt() {
    if (state == State.HALF_OPEN) {
      halfOpenCalls++;
    }
  }
}
