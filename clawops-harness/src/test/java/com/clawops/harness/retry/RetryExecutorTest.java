package com.clawops.harness.retry;

import static org.assertj.core.api.Assertions.assertThat;

import com.clawops.harness.spi.ToolResult;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RetryExecutor 重试与熔断")
class RetryExecutorTest {

  private RetryExecutor executor;

  @BeforeEach
  void setUp() {
    executor = new RetryExecutor();
  }

  @AfterEach
  void tearDown() {
    executor.close();
  }

  @Test
  @DisplayName("成功时只执行一次")
  void shouldReturnImmediatelyOnSuccess() {
    AtomicInteger counter = new AtomicInteger();
    RetryConfig config = new RetryConfig(3, 0, 100, 1000);
    CircuitBreaker cb = new CircuitBreaker(5, Duration.ofMillis(100), 1);

    ToolResult result =
        executor.execute(
            () -> {
              counter.incrementAndGet();
              return ToolResult.success("ok");
            },
            cb,
            config,
            "op");

    assertThat(result.status()).isEqualTo(ToolResult.STATUS_SUCCESS);
    assertThat(counter.get()).isEqualTo(1);
  }

  @Test
  @DisplayName("失败后按配置重试并在成功时返回")
  void shouldRetryUntilSuccess() {
    AtomicInteger counter = new AtomicInteger();
    RetryConfig config = new RetryConfig(3, 0, 100, 1000);
    CircuitBreaker cb = new CircuitBreaker(5, Duration.ofMillis(100), 1);

    ToolResult result =
        executor.execute(
            () -> {
              if (counter.incrementAndGet() < 3) {
                return ToolResult.error("fail");
              }
              return ToolResult.success("ok");
            },
            cb,
            config,
            "op");

    assertThat(result.status()).isEqualTo(ToolResult.STATUS_SUCCESS);
    assertThat(counter.get()).isEqualTo(3);
  }

  @Test
  @DisplayName("重试耗尽后返回降级结果")
  void shouldReturnDegradedAfterRetriesExhausted() {
    AtomicInteger counter = new AtomicInteger();
    RetryConfig config = new RetryConfig(2, 0, 100, 1000);
    CircuitBreaker cb = new CircuitBreaker(5, Duration.ofMillis(100), 1);

    ToolResult result =
        executor.execute(
            () -> {
              counter.incrementAndGet();
              return ToolResult.error("fail");
            },
            cb,
            config,
            "op");

    assertThat(result.status()).isEqualTo(ToolResult.STATUS_DEGRADED);
    assertThat(result.content()).contains("重试耗尽");
    assertThat(counter.get()).isEqualTo(2);
  }

  @Test
  @DisplayName("超时后返回降级结果")
  void shouldReturnDegradedOnTimeout() {
    RetryConfig config = new RetryConfig(1, 0, 100, 50);
    CircuitBreaker cb = new CircuitBreaker(5, Duration.ofMillis(100), 1);

    ToolResult result =
        executor.execute(
            () -> {
              try {
                Thread.sleep(2000);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
              return ToolResult.success("too late");
            },
            cb,
            config,
            "slow-op");

    assertThat(result.status()).isEqualTo(ToolResult.STATUS_DEGRADED);
    assertThat(result.content()).contains("slow-op");
  }

  @Test
  @DisplayName("熔断器打开后跳过执行")
  void shouldSkipExecutionWhenCircuitOpen() {
    AtomicInteger counter = new AtomicInteger();
    RetryConfig config = new RetryConfig(1, 0, 100, 1000);
    CircuitBreaker cb = new CircuitBreaker(1, Duration.ofHours(1), 1);

    executor.execute(() -> ToolResult.error("boom"), cb, config, "fragile");
    ToolResult result =
        executor.execute(
            () -> {
              counter.incrementAndGet();
              return ToolResult.success("ok");
            },
            cb,
            config,
            "fragile");

    assertThat(cb.state()).isEqualTo(CircuitBreaker.State.OPEN);
    assertThat(result.status()).isEqualTo(ToolResult.STATUS_DEGRADED);
    assertThat(result.content()).contains("熔断器打开");
    assertThat(counter.get()).isZero();
  }
}
