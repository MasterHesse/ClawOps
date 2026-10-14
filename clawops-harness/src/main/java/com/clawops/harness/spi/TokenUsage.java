package com.clawops.harness.spi;

/**
 * 单次模型调用的 Token 用量。
 *
 * @param inputTokens 输入 token 数
 * @param outputTokens 输出 token 数
 * @param totalTokens 总 token 数
 */
public record TokenUsage(int inputTokens, int outputTokens, int totalTokens) {

  public TokenUsage {
    totalTokens = Math.max(0, totalTokens);
    inputTokens = Math.max(0, inputTokens);
    outputTokens = Math.max(0, outputTokens);
  }

  public static TokenUsage zero() {
    return new TokenUsage(0, 0, 0);
  }

  public TokenUsage plus(TokenUsage other) {
    return new TokenUsage(
        this.inputTokens + other.inputTokens,
        this.outputTokens + other.outputTokens,
        this.totalTokens + other.totalTokens);
  }
}
