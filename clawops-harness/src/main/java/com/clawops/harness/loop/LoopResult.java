package com.clawops.harness.loop;

import com.clawops.harness.spi.TokenUsage;

/**
 * AgentLoop 运行结果。
 *
 * @param status 结果状态
 * @param answer 最终回答（非成功时为空或错误说明）
 * @param steps 实际执行步数
 * @param tokenUsage token 用量
 */
public record LoopResult(Status status, String answer, int steps, TokenUsage tokenUsage) {

  public enum Status {
    SUCCESS,
    MAX_STEPS,
    TIMEOUT,
    LOOP_DETECTED,
    ERROR
  }

  public static LoopResult success(String answer, int steps, TokenUsage usage) {
    return new LoopResult(Status.SUCCESS, answer, steps, usage);
  }

  public static LoopResult maxSteps(int steps, TokenUsage usage) {
    return new LoopResult(Status.MAX_STEPS, "已达到最大步数", steps, usage);
  }

  public static LoopResult timeout(int steps, TokenUsage usage) {
    return new LoopResult(Status.TIMEOUT, "运行超时", steps, usage);
  }

  public static LoopResult loopDetected(String answer, int steps, TokenUsage usage) {
    return new LoopResult(Status.LOOP_DETECTED, answer, steps, usage);
  }

  public static LoopResult error(String answer, int steps, TokenUsage usage) {
    return new LoopResult(Status.ERROR, answer, steps, usage);
  }
}
