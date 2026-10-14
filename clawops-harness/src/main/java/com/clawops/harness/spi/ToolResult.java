package com.clawops.harness.spi;

/**
 * 工具执行结果。
 *
 * <p>状态分为 success / error / degraded / forbidden / timeout / loop_detected，AgentLoop 根据状态决定下一步行为。
 */
public record ToolResult(String status, String content) {

  public static final String STATUS_SUCCESS = "success";
  public static final String STATUS_ERROR = "error";
  public static final String STATUS_DEGRADED = "degraded";
  public static final String STATUS_FORBIDDEN = "forbidden";
  public static final String STATUS_TIMEOUT = "timeout";
  public static final String STATUS_LOOP_DETECTED = "loop_detected";

  public static ToolResult success(String content) {
    return new ToolResult(STATUS_SUCCESS, content);
  }

  public static ToolResult error(String content) {
    return new ToolResult(STATUS_ERROR, content);
  }

  public static ToolResult degraded(String content) {
    return new ToolResult(STATUS_DEGRADED, content);
  }

  public static ToolResult forbidden(String content) {
    return new ToolResult(STATUS_FORBIDDEN, content);
  }

  public static ToolResult timeout(String content) {
    return new ToolResult(STATUS_TIMEOUT, content);
  }

  public static ToolResult loopDetected(String content) {
    return new ToolResult(STATUS_LOOP_DETECTED, content);
  }

  public boolean isSuccess() {
    return STATUS_SUCCESS.equals(status);
  }

  public boolean isDegraded() {
    return STATUS_DEGRADED.equals(status);
  }
}
