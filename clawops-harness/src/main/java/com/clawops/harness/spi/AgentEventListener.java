package com.clawops.harness.spi;

/**
 * AgentLoop 事件监听器。
 *
 * <p>gateway 层实现此接口，将事件转换为 SSE 推送到前端；测试侧可实现以收集断言数据。
 */
public interface AgentEventListener {

  default void onDelta(String text) {}

  default void onToolCall(ToolCall call) {}

  default void onToolResult(String toolCallId, ToolResult result) {}

  default void onDone(String answer, int steps, TokenUsage usage) {}

  default void onError(Throwable error) {}
}
