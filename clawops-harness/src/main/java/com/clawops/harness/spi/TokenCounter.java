package com.clawops.harness.spi;

import java.util.List;

/**
 * Token 计数器。
 *
 * <p>harness 主代码只依赖此接口；真实 tokenizer 由上层 adapter 注入，测试中可用近似实现。
 */
@FunctionalInterface
public interface TokenCounter {

  /**
   * 估算文本对应的 token 数量。
   *
   * @param text 文本
   * @return token 数（非负）
   */
  int count(String text);

  /**
   * 估算消息列表的总 token 数。
   *
   * @param messages 消息列表
   * @return token 总数
   */
  default int countMessages(List<AgentMessage> messages) {
    if (messages == null || messages.isEmpty()) {
      return 0;
    }
    int sum = 0;
    for (AgentMessage message : messages) {
      sum += count(message.content() == null ? "" : message.content());
      if (message.hasToolCalls()) {
        for (ToolCall call : message.toolCalls()) {
          sum += count(call.name());
          if (call.arguments() != null) {
            sum += count(call.arguments().toString());
          }
        }
      }
    }
    return sum;
  }
}
