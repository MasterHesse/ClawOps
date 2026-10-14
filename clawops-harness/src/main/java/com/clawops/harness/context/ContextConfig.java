package com.clawops.harness.context;

/**
 * 上下文管理配置。
 *
 * @param maxTokens 最大 token 预算（默认 4000）
 * @param compressionThresholdRatio 触发压缩的阈值比例（默认 0.8）
 * @param maxHistoryMessages 历史消息硬上限（默认 20）
 */
public record ContextConfig(
    int maxTokens, double compressionThresholdRatio, int maxHistoryMessages) {

  public static final int DEFAULT_MAX_TOKENS = 4000;
  public static final double DEFAULT_THRESHOLD = 0.8;
  public static final int DEFAULT_MAX_HISTORY = 20;

  public ContextConfig {
    if (maxTokens < 1) {
      throw new IllegalArgumentException("maxTokens must be >= 1");
    }
    if (compressionThresholdRatio <= 0 || compressionThresholdRatio > 1) {
      throw new IllegalArgumentException("compressionThresholdRatio must be in (0, 1]");
    }
    if (maxHistoryMessages < 1) {
      throw new IllegalArgumentException("maxHistoryMessages must be >= 1");
    }
  }

  public ContextConfig() {
    this(DEFAULT_MAX_TOKENS, DEFAULT_THRESHOLD, DEFAULT_MAX_HISTORY);
  }

  public int compressionThresholdTokens() {
    return (int) (maxTokens * compressionThresholdRatio);
  }
}
