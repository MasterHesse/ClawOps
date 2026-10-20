package com.clawops.harness.loop;

import com.clawops.harness.spi.TokenUsage;
import com.clawops.harness.spi.ToolCall;

/**
 * AgentLoop 运行结果。
 *
 * @param status 结果状态
 * @param answer 最终回答（非成功时为空或错误说明）
 * @param steps 实际执行步数
 * @param tokenUsage token 用量
 * @param pendingToolCall PAUSED_FOR_APPROVAL 时携带待审批的工具调用
 * @param approvalId PAUSED_FOR_APPROVAL 时携带审批单号
 */
public record LoopResult(
    Status status,
    String answer,
    int steps,
    TokenUsage tokenUsage,
    ToolCall pendingToolCall,
    String approvalId) {

  public enum Status {
    SUCCESS,
    MAX_STEPS,
    TIMEOUT,
    LOOP_DETECTED,
    PAUSED_FOR_APPROVAL,
    ERROR
  }

  public static LoopResult success(String answer, int steps, TokenUsage usage) {
    return new LoopResult(Status.SUCCESS, answer, steps, usage, null, null);
  }

  public static LoopResult maxSteps(int steps, TokenUsage usage) {
    return new LoopResult(Status.MAX_STEPS, "已达到最大步数", steps, usage, null, null);
  }

  public static LoopResult timeout(int steps, TokenUsage usage) {
    return new LoopResult(Status.TIMEOUT, "运行超时", steps, usage, null, null);
  }

  public static LoopResult loopDetected(String answer, int steps, TokenUsage usage) {
    return new LoopResult(Status.LOOP_DETECTED, answer, steps, usage, null, null);
  }

  public static LoopResult error(String answer, int steps, TokenUsage usage) {
    return new LoopResult(Status.ERROR, answer, steps, usage, null, null);
  }

  /** 审批暂停：携带待审批工具调用与审批单号。 */
  public static LoopResult paused(
      ToolCall pendingCall, String approvalId, int steps, TokenUsage usage) {
    return new LoopResult(Status.PAUSED_FOR_APPROVAL, null, steps, usage, pendingCall, approvalId);
  }
}
