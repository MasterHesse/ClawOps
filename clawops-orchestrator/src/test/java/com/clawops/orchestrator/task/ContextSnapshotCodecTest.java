package com.clawops.orchestrator.task;

import static org.assertj.core.api.Assertions.assertThat;

import com.clawops.harness.spi.AgentMessage;
import com.clawops.harness.spi.TokenUsage;
import com.clawops.harness.spi.ToolCall;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** ContextSnapshotCodec 上下文与 token 编解码（纯单测，conventions/testing.md §3）。 */
@DisplayName("ContextSnapshotCodec 上下文与 token 编解码")
class ContextSnapshotCodecTest {

  private ContextSnapshotCodec codec;

  @BeforeEach
  void setUp() {
    codec = new ContextSnapshotCodec(new ObjectMapper());
  }

  @Test
  @DisplayName("上下文消息 round-trip：user / assistant(text) / assistant(toolCalls) / tool")
  void shouldRoundTripAllMessageTypes() {
    ToolCall call = new ToolCall("call-1", "metrics.query", Map.of("q", "up"));
    List<AgentMessage> context =
        List.of(
            AgentMessage.user("诊断"),
            AgentMessage.assistant("已分析"),
            AgentMessage.assistant(call),
            AgentMessage.tool("call-1", "[metrics.query] up=1"));

    List<AgentMessage> restored = codec.deserializeContext(codec.serialize(context));

    assertThat(restored).hasSize(4);
    assertThat(restored.get(0).role()).isEqualTo(AgentMessage.Role.USER);
    assertThat(restored.get(0).content()).isEqualTo("诊断");
    assertThat(restored.get(2).hasToolCalls()).isTrue();
    assertThat(restored.get(2).toolCalls().get(0).name()).isEqualTo("metrics.query");
    assertThat(restored.get(2).toolCalls().get(0).arguments()).containsEntry("q", "up");
    assertThat(restored.get(3).role()).isEqualTo(AgentMessage.Role.TOOL);
    assertThat(restored.get(3).toolCallId()).isEqualTo("call-1");
  }

  @Test
  @DisplayName("空与 null 上下文归一为空列表")
  void shouldNormalizeEmpty() {
    assertThat(codec.deserializeContext(null)).isEmpty();
    assertThat(codec.deserializeContext("")).isEmpty();
    assertThat(codec.deserializeContext(codec.serialize(List.of()))).isEmpty();
  }

  @Test
  @DisplayName("token 用量 round-trip，null 归一为 zero")
  void shouldRoundTripTokenUsage() {
    TokenUsage usage = new TokenUsage(100, 50, 150);
    assertThat(codec.deserializeToken(codec.serialize(usage))).isEqualTo(usage);
    assertThat(codec.deserializeToken(null)).isEqualTo(TokenUsage.zero());
    assertThat(codec.deserializeToken("")).isEqualTo(TokenUsage.zero());
  }

  @Test
  @DisplayName("工具入参 Map round-trip，null 归一为空")
  void shouldRoundTripArguments() {
    Map<String, Object> args = Map.of("promql", "up", "limit", 5);
    Map<String, Object> restored = codec.deserializeArguments(codec.serializeArguments(args));
    assertThat(restored).containsEntry("promql", "up").containsEntry("limit", 5);

    assertThat(codec.deserializeArguments(null)).isEmpty();
    assertThat(codec.deserializeArguments("")).isEmpty();
  }
}
