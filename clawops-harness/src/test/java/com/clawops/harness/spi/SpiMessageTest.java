package com.clawops.harness.spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Harness SPI 基础消息模型")
class SpiMessageTest {

  @Test
  @DisplayName("权限等级可正确比较覆盖关系")
  void shouldComparePermissionLevels() {
    assertThat(Permission.DANGEROUS.covers(Permission.READ)).isTrue();
    assertThat(Permission.WRITE.covers(Permission.READ)).isTrue();
    assertThat(Permission.READ.covers(Permission.WRITE)).isFalse();
    assertThat(Permission.WRITE.covers(Permission.DANGEROUS)).isFalse();
  }

  @Test
  @DisplayName("AgentMessage 工厂方法生成正确角色")
  void shouldCreateMessagesWithCorrectRoles() {
    AgentMessage system = AgentMessage.system("sys");
    AgentMessage user = AgentMessage.user("hi");
    AgentMessage assistant = AgentMessage.assistant("hello");

    assertThat(system.role()).isEqualTo(AgentMessage.Role.SYSTEM);
    assertThat(user.role()).isEqualTo(AgentMessage.Role.USER);
    assertThat(assistant.role()).isEqualTo(AgentMessage.Role.ASSISTANT);
    assertThat(assistant.hasToolCalls()).isFalse();
  }

  @Test
  @DisplayName("assistant 可携带工具调用")
  void shouldCreateAssistantMessageWithToolCalls() {
    ToolCall call = new ToolCall("id-1", "metrics.query", Map.of("q", "up"));
    AgentMessage msg = AgentMessage.assistant(call);

    assertThat(msg.role()).isEqualTo(AgentMessage.Role.ASSISTANT);
    assertThat(msg.hasToolCalls()).isTrue();
    assertThat(msg.toolCalls()).containsExactly(call);
  }

  @Test
  @DisplayName("ToolResult 工厂方法生成对应状态")
  void shouldCreateToolResultsWithStatus() {
    assertThat(ToolResult.success("ok").status()).isEqualTo(ToolResult.STATUS_SUCCESS);
    assertThat(ToolResult.error("bad").isSuccess()).isFalse();
    assertThat(ToolResult.degraded("fallback").isDegraded()).isTrue();
    assertThat(ToolResult.forbidden("no").status()).isEqualTo(ToolResult.STATUS_FORBIDDEN);
  }

  @Test
  @DisplayName("TokenUsage 可累加")
  void shouldAccumulateTokenUsage() {
    TokenUsage a = new TokenUsage(10, 5, 15);
    TokenUsage b = new TokenUsage(3, 2, 5);

    TokenUsage sum = a.plus(b);
    assertThat(sum.inputTokens()).isEqualTo(13);
    assertThat(sum.outputTokens()).isEqualTo(7);
    assertThat(sum.totalTokens()).isEqualTo(20);
  }
}
