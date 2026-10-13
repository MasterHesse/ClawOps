package com.clawops.gateway.api;

/**
 * 错误码契约（conventions/api.md §3）。
 *
 * <p>分段：1xxx 通用 | 2xxx 任务域 | 3xxx 工具执行 | 4xxx RAG | 5xxx 集成 | 9xxx 系统内部。 一经发布只增不改不复用；机器可读版本见
 * src/main/resources/error-codes.yaml（Agent 三阶防护的降级判断、前端提示、CLI 输出共用），枚举与 yaml 的一致性由
 * ApiErrorCodeContractTest 校验。
 */
public enum ApiErrorCode {
  OK(0, "ok"),
  INVALID_PARAM(1001, "参数错误"),
  UNAUTHORIZED(1002, "未认证"),
  FORBIDDEN(1003, "无权限"),
  NOT_FOUND(1004, "资源不存在"),
  RATE_LIMITED(1005, "请求过于频繁"),
  TASK_STATE_CONFLICT(2001, "任务状态冲突"),
  TOOL_PARAM_INVALID(3001, "工具参数非法"),
  TOOL_TIMEOUT(3002, "工具执行超时"),
  TOOL_RETRY_EXHAUSTED(3003, "工具重试耗尽"),
  TOOL_CIRCUIT_OPEN(3004, "工具熔断开启"),
  KB_PARSE_FAILED(4001, "知识库文档解析失败"),
  WEBHOOK_SIGNATURE_INVALID(5001, "Webhook 签名校验失败"),
  INTERNAL(9001, "系统内部错误");

  private final int code;
  private final String defaultMessage;

  ApiErrorCode(int code, String defaultMessage) {
    this.code = code;
    this.defaultMessage = defaultMessage;
  }

  public int code() {
    return code;
  }

  public String defaultMessage() {
    return defaultMessage;
  }

  public static ApiErrorCode fromCode(int code) {
    for (ApiErrorCode value : values()) {
      if (value.code == code) {
        return value;
      }
    }
    throw new IllegalArgumentException("未知错误码: " + code);
  }
}
