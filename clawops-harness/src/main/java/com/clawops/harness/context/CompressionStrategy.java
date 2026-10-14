package com.clawops.harness.context;

import com.clawops.harness.spi.AgentMessage;
import com.clawops.harness.spi.TokenCounter;
import java.util.List;

/** 上下文压缩策略。 */
@FunctionalInterface
public interface CompressionStrategy {

  /**
   * 压缩可压缩部分的历史消息，使其 token 数不超过预算。
   *
   * @param history 可压缩的历史消息（不含 System/Role/当前任务）
   * @param counter token 计数器
   * @param budget token 预算
   * @return 压缩后的消息列表
   */
  List<AgentMessage> compress(List<AgentMessage> history, TokenCounter counter, int budget);
}
