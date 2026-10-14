package com.clawops.gateway.chat;

import com.clawops.harness.spi.ToolCall;
import com.clawops.harness.spi.ToolResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 将 Harness 事件转换为 SSE 事件。 */
final class SseEventBuilder {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private SseEventBuilder() {}

  static SseEmitter.SseEventBuilder delta(String text) {
    return event("delta", Map.of("text", text));
  }

  static SseEmitter.SseEventBuilder toolCall(ToolCall call) {
    return event(
        "tool_call", Map.of("id", call.id(), "name", call.name(), "arguments", call.arguments()));
  }

  static SseEmitter.SseEventBuilder toolResult(String toolCallId, ToolResult result) {
    return event(
        "tool_result",
        Map.of("id", toolCallId, "status", result.status(), "content", result.content()));
  }

  static SseEmitter.SseEventBuilder done(String taskId, int steps) {
    return event("done", Map.of("taskId", taskId, "steps", steps));
  }

  static SseEmitter.SseEventBuilder error(String message) {
    return event("error", Map.of("message", message));
  }

  private static SseEmitter.SseEventBuilder event(String name, Object data) {
    try {
      return SseEmitter.event().name(name).data(MAPPER.writeValueAsString(data));
    } catch (JsonProcessingException e) {
      return SseEmitter.event().name("error").data("{\"message\":\"序列化失败\"}");
    }
  }
}
