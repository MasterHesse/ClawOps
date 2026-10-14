package com.clawops.gateway.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.clawops.harness.spi.ToolCall;
import com.clawops.harness.spi.ToolResult;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SseEventBuilder 事件序列化")
class SseEventBuilderTest {

  @Test
  @DisplayName("可生成 delta 事件")
  void shouldBuildDeltaEvent() {
    assertThat(SseEventBuilder.delta("hello")).isNotNull();
  }

  @Test
  @DisplayName("可生成 tool_call 事件")
  void shouldBuildToolCallEvent() {
    ToolCall call = new ToolCall("id-1", "metrics.query", Map.of("promql", "up"));
    assertThat(SseEventBuilder.toolCall(call)).isNotNull();
  }

  @Test
  @DisplayName("可生成 tool_result 事件")
  void shouldBuildToolResultEvent() {
    assertThat(SseEventBuilder.toolResult("id-1", ToolResult.success("ok"))).isNotNull();
  }

  @Test
  @DisplayName("可生成 done 与 error 事件")
  void shouldBuildDoneAndErrorEvents() {
    assertThat(SseEventBuilder.done("task-1", 3)).isNotNull();
    assertThat(SseEventBuilder.error("boom")).isNotNull();
  }
}
