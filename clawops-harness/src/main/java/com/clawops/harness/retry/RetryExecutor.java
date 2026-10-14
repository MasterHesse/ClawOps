package com.clawops.harness.retry;

import com.clawops.harness.spi.ToolResult;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * 带超时、指数退避、熔断的重试执行器。
 *
 * <p>执行失败（抛出异常或返回 error 状态）时按 {@link RetryConfig} 重试；重试耗尽或熔断打开时返回降级结果。
 */
public class RetryExecutor implements AutoCloseable {

  private final ExecutorService executor;

  public RetryExecutor() {
    this(Executors.newVirtualThreadPerTaskExecutor());
  }

  public RetryExecutor(ExecutorService executor) {
    this.executor = executor;
  }

  /**
   * 执行任务。
   *
   * @param task 任务
   * @param circuitBreaker 熔断器
   * @param config 重试配置
   * @param operationName 操作名（用于降级说明）
   * @return 执行结果；熔断或重试耗尽时返回 degraded 状态
   */
  public ToolResult execute(
      Supplier<ToolResult> task,
      CircuitBreaker circuitBreaker,
      RetryConfig config,
      String operationName) {
    if (!circuitBreaker.allow()) {
      return ToolResult.degraded("熔断器打开，跳过操作: " + operationName);
    }

    circuitBreaker.recordAttempt();
    int attempt = 1;
    Throwable lastError = null;

    while (attempt <= config.maxAttempts()) {
      try {
        ToolResult result = runWithTimeout(task, config.timeoutMillis());
        if (isFailure(result)) {
          circuitBreaker.recordFailure();
          lastError = new RuntimeException(result.content());
        } else {
          circuitBreaker.recordSuccess();
          return result;
        }
      } catch (TimeoutException e) {
        circuitBreaker.recordFailure();
        lastError = e;
      } catch (Exception e) {
        circuitBreaker.recordFailure();
        lastError = e;
      }

      if (attempt < config.maxAttempts()) {
        sleep(config.computeDelay(attempt + 1));
      }
      attempt++;
    }

    String reason = lastError == null ? "未知错误" : lastError.getMessage();
    return ToolResult.degraded("操作 " + operationName + " 重试耗尽: " + reason);
  }

  private ToolResult runWithTimeout(Supplier<ToolResult> task, long timeoutMillis)
      throws Exception {
    try {
      return CompletableFuture.supplyAsync(task, executor)
          .orTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
          .join();
    } catch (java.util.concurrent.CompletionException e) {
      if (e.getCause() instanceof TimeoutException timeout) {
        throw timeout;
      }
      if (e.getCause() instanceof Exception ex) {
        throw ex;
      }
      throw new RuntimeException(e.getCause());
    }
  }

  private boolean isFailure(ToolResult result) {
    return result == null
        || ToolResult.STATUS_ERROR.equals(result.status())
        || ToolResult.STATUS_TIMEOUT.equals(result.status());
  }

  private void sleep(long millis) {
    if (millis <= 0) {
      return;
    }
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public void close() {
    executor.shutdown();
  }
}
