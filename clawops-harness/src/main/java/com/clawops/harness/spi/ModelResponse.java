package com.clawops.harness.spi;

import java.util.List;

/**
 * 模型返回结果。
 *
 * @param text 纯文本回复（无工具调用时）
 * @param toolCalls 工具调用请求列表
 * @param tokenUsage 本次调用 token 用量
 */
public record ModelResponse(String text, List<ToolCall> toolCalls, TokenUsage tokenUsage) {

  public ModelResponse {
    toolCalls = List.copyOf(toolCalls == null ? List.of() : toolCalls);
  }

  public boolean hasToolCalls() {
    return !toolCalls.isEmpty();
  }

  public static ModelResponse text(String text, TokenUsage usage) {
    return new ModelResponse(text, List.of(), usage);
  }

  public static ModelResponse toolCalls(List<ToolCall> calls, TokenUsage usage) {
    return new ModelResponse(null, calls, usage);
  }
}
