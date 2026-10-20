package com.clawops.harness.loop;

import com.clawops.harness.spi.AgentMessage;
import com.clawops.harness.spi.ModelClient;
import com.clawops.harness.spi.ModelResponse;
import com.clawops.harness.spi.TokenUsage;
import com.clawops.harness.spi.ToolCall;
import com.clawops.harness.spi.ToolDefinition;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;

/** 测试中使用的脚本化模型客户端（记录每次请求的消息列表供断言）。 */
class StubModelClient implements ModelClient {

  private final Deque<ModelResponse> queue = new ArrayDeque<>();
  final List<List<AgentMessage>> lastRequests = new ArrayList<>();

  void enqueueText(String text) {
    queue.add(ModelResponse.text(text, new TokenUsage(1, 1, 2)));
  }

  void enqueueToolCall(ToolCall call) {
    queue.add(ModelResponse.toolCalls(List.of(call), new TokenUsage(2, 1, 3)));
  }

  @Override
  public ModelResponse chat(List<AgentMessage> messages, Collection<ToolDefinition> tools) {
    lastRequests.add(List.copyOf(messages));
    ModelResponse response = queue.poll();
    if (response == null) {
      throw new IllegalStateException("StubModelClient 无预设回复");
    }
    return response;
  }
}
