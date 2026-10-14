package com.clawops.harness.context;

import com.clawops.harness.spi.TokenCounter;

/**
 * 默认 token 计数器。
 *
 * <p>W1 使用字符数/4 做近似；真实场景由上层 adapter 注入 LLM tokenizer。
 */
public class DefaultTokenCounter implements TokenCounter {

  @Override
  public int count(String text) {
    if (text == null || text.isEmpty()) {
      return 0;
    }
    return Math.max(1, text.length() / 4);
  }
}
