package com.clawops.gateway.api;

import java.util.UUID;

/**
 * 统一响应体（conventions/api.md §2）。
 *
 * <pre>{ "code": 0, "message": "ok", "data": {}, "traceId": "8f3a..." }</pre>
 *
 * @param <T> data 载荷类型
 */
public record ApiResponse<T>(int code, String message, T data, String traceId) {

  public static <T> ApiResponse<T> ok(T data) {
    return new ApiResponse<>(ApiErrorCode.OK.code(), "ok", data, newTraceId());
  }

  public static ApiResponse<Void> error(ApiErrorCode error, String traceId) {
    return new ApiResponse<>(error.code(), error.defaultMessage(), null, traceId);
  }

  private static String newTraceId() {
    return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
  }
}
