package com.clawops.gateway.chat;

import com.clawops.harness.spi.AgentMessage;
import com.clawops.harness.spi.ModelClient;
import com.clawops.harness.spi.ModelResponse;
import com.clawops.harness.spi.TokenUsage;
import com.clawops.harness.spi.ToolCall;
import com.clawops.harness.spi.ToolDefinition;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * W1 演示用模型客户端。
 *
 * <p>根据用户关键词返回固定工具调用或文本回复，用于在不消耗真实 LLM API 的情况下验证工具调用链与 SSE 流式。 后续迭代中将替换为 LangChain4j 适配器或真实模型。
 */
public class DemoModelClient implements ModelClient {

  @Override
  public ModelResponse chat(List<AgentMessage> messages, Collection<ToolDefinition> tools) {
    String lastUser = findLastUserMessage(messages);
    if (lastUser == null) {
      return ModelResponse.text("请描述你的问题", TokenUsage.zero());
    }
    String lower = lastUser.toLowerCase();

    if (containsAny(lower, "指标", "metric", "prometheus", "cpu", "内存", "memory")) {
      return toolResponse("metrics.query", Map.of("promql", "up"));
    }
    if (containsAny(lower, "日志", "log", "error", "错误")) {
      return toolResponse("log.search", Map.of("level", "ERROR", "keyword", ""));
    }

    return ModelResponse.text(
        "我可以帮你查询 Prometheus 指标或搜索日志，请尝试说「查询 CPU 指标」或「查看 ERROR 日志」。", TokenUsage.zero());
  }

  private ModelResponse toolResponse(String toolName, Map<String, Object> arguments) {
    String callId = "demo-call-" + System.nanoTime();
    return ModelResponse.toolCalls(
        List.of(new ToolCall(callId, toolName, arguments)), TokenUsage.zero());
  }

  private String findLastUserMessage(List<AgentMessage> messages) {
    for (int i = messages.size() - 1; i >= 0; i--) {
      AgentMessage message = messages.get(i);
      if (message.role() == AgentMessage.Role.USER && message.content() != null) {
        return message.content();
      }
    }
    return null;
  }

  private boolean containsAny(String text, String... needles) {
    for (String needle : needles) {
      if (text.contains(needle)) {
        return true;
      }
    }
    return false;
  }
}
