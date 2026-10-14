package com.clawops.harness.loop;

/**
 * AgentLoop 运行配置。
 *
 * @param maxSteps 最大步数（默认 10）
 * @param maxDurationSeconds 最大运行时长（默认 60）
 * @param systemPrompt 系统提示
 * @param rolePrompt 角色提示
 */
public record LoopConfig(
    int maxSteps, int maxDurationSeconds, String systemPrompt, String rolePrompt) {

  public static final int DEFAULT_MAX_STEPS = 10;
  public static final int DEFAULT_MAX_DURATION_SECONDS = 60;

  public LoopConfig {
    if (maxSteps < 1) {
      throw new IllegalArgumentException("maxSteps must be >= 1");
    }
    if (maxDurationSeconds < 1) {
      throw new IllegalArgumentException("maxDurationSeconds must be >= 1");
    }
  }

  public LoopConfig() {
    this(DEFAULT_MAX_STEPS, DEFAULT_MAX_DURATION_SECONDS, "", "");
  }
}
