package com.clawops.harness.context;

import com.clawops.harness.spi.AgentMessage;
import com.clawops.harness.spi.TokenCounter;
import java.util.ArrayList;
import java.util.List;

/**
 * 窗口压缩策略。
 *
 * <p>保留最近的对话消息，丢弃超出 token 预算的旧消息，并插入一条摘要占位消息。
 */
public class WindowCompressionStrategy implements CompressionStrategy {

  @Override
  public List<AgentMessage> compress(List<AgentMessage> history, TokenCounter counter, int budget) {
    if (history == null || history.isEmpty()) {
      return List.of();
    }

    List<AgentMessage> kept = new ArrayList<>(history);
    int removed = 0;
    while (counter.countMessages(kept) > budget && kept.size() > 2) {
      kept.removeFirst();
      removed++;
    }

    if (removed > 0) {
      List<AgentMessage> result = new ArrayList<>();
      result.add(AgentMessage.system("[上下文已压缩] 已省略 " + removed + " 条较早消息，保留目标、当前任务与最近对话。"));
      result.addAll(kept);
      return result;
    }
    return List.copyOf(kept);
  }
}
