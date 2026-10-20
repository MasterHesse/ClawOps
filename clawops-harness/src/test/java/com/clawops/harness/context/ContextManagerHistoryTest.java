package com.clawops.harness.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.clawops.harness.spi.AgentMessage;
import com.clawops.harness.spi.ToolCall;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** ContextManager 历史注入与未解析工具调用裁剪（断点恢复用，conventions/testing.md §3 TDD）。 */
@DisplayName("ContextManager 历史注入与尾部裁剪")
class ContextManagerHistoryTest {

  @Test
  @DisplayName("withHistory 注入历史后 build 包含这些消息")
  void shouldIncludeInjectedHistoryInBuild() {
    ContextManager ctx =
        new ContextManager()
            .withTask("诊断")
            .withHistory(List.of(AgentMessage.user("查 CPU"), AgentMessage.assistant("已查")));

    List<AgentMessage> built = ctx.build();
    assertThat(built).extracting(AgentMessage::content).contains("查 CPU", "已查");
  }

  @Test
  @DisplayName("withHistory 多次调用以最后一次为准（替换而非追加）")
  void shouldReplaceHistoryOnRepeatedWithHistory() {
    ContextManager ctx = new ContextManager();
    ctx.withHistory(List.of(AgentMessage.user("旧")));
    ctx.withHistory(List.of(AgentMessage.user("新")));

    assertThat(ctx.historySize()).isEqualTo(1);
    assertThat(ctx.build()).extracting(AgentMessage::content).contains("新");
  }

  @Test
  @DisplayName("trimUnresolvedToolCalls 移除尾部未配对 tool result 的 assistant toolCalls 消息")
  void shouldTrimTrailingUnresolvedAssistantToolCalls() {
    ToolCall call = new ToolCall("call-1", "metrics.query", Map.of("q", "up"));
    ContextManager ctx =
        new ContextManager()
            .withHistory(
                List.of(
                    AgentMessage.user("查指标"), AgentMessage.assistant(call) // 尾部未配对 → 应被移除
                    ));

    ctx.trimUnresolvedToolCalls();

    assertThat(ctx.historySize()).isEqualTo(1);
    assertThat(ctx.build())
        .filteredOn(m -> m.role() == AgentMessage.Role.ASSISTANT && m.hasToolCalls())
        .isEmpty();
  }

  @Test
  @DisplayName("已配对的 assistant toolCalls + tool result 均保留")
  void shouldKeepResolvedToolCallPairs() {
    ToolCall call = new ToolCall("call-1", "metrics.query", Map.of("q", "up"));
    ContextManager ctx =
        new ContextManager()
            .withHistory(
                List.of(
                    AgentMessage.user("查指标"),
                    AgentMessage.assistant(call),
                    AgentMessage.tool("call-1", "[metrics.query] up=1")));

    ctx.trimUnresolvedToolCalls();

    assertThat(ctx.historySize()).isEqualTo(3);
  }

  @Test
  @DisplayName("连续多个尾部未解析 assistant toolCalls 全部移除，已配对的保留")
  void shouldTrimAllConsecutiveUnresolvedTrailing() {
    ToolCall c1 = new ToolCall("c1", "metrics.query", Map.of());
    ToolCall c2 = new ToolCall("c2", "log.search", Map.of());
    ContextManager ctx =
        new ContextManager()
            .withHistory(
                List.of(
                    AgentMessage.user("查"),
                    AgentMessage.assistant(c1),
                    AgentMessage.tool("c1", "ok"),
                    AgentMessage.assistant(c2) // 尾部未配对
                    ));

    ctx.trimUnresolvedToolCalls();

    assertThat(ctx.historySize()).isEqualTo(3);
    assertThat(ctx.build())
        .filteredOn(m -> m.role() == AgentMessage.Role.ASSISTANT && m.hasToolCalls())
        .hasSize(1);
  }
}
