package com.clawops.harness.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.clawops.harness.spi.AgentMessage;
import com.clawops.harness.spi.ModelResponse;
import com.clawops.harness.spi.Permission;
import com.clawops.harness.spi.TokenCounter;
import com.clawops.harness.spi.TokenUsage;
import com.clawops.harness.spi.ToolCall;
import com.clawops.harness.spi.ToolDefinition;
import com.clawops.harness.spi.ToolResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ContextManager 分层 Prompt 与压缩")
class ContextManagerTest {

  private final ToolDefinition sampleTool =
      new ToolDefinition("sample", "示例工具", Permission.READ, Map.of(), call -> null, null);

  @Test
  @DisplayName("build 输出包含系统、角色、工具、任务与历史消息")
  void shouldBuildLayeredPrompt() {
    ContextManager ctx =
        new ContextManager()
            .withSystemPrompt("sys")
            .withRolePrompt("role")
            .withTools(List.of(sampleTool))
            .withTask("诊断 inventory-service")
            .addUserMessage("查询 CPU");

    List<AgentMessage> messages = ctx.build();

    assertThat(messages)
        .extracting(AgentMessage::role)
        .containsExactly(
            AgentMessage.Role.SYSTEM,
            AgentMessage.Role.SYSTEM,
            AgentMessage.Role.SYSTEM,
            AgentMessage.Role.USER,
            AgentMessage.Role.USER);
  }

  @Test
  @DisplayName("超过 token 阈值时触发压缩并出现摘要占位")
  void shouldCompressWhenExceedingThreshold() {
    TokenCounter expensiveCounter = text -> text == null ? 0 : text.length();
    ContextConfig config = new ContextConfig(80, 0.5, 20);
    ContextManager ctx =
        new ContextManager(config, expensiveCounter, new WindowCompressionStrategy())
            .withSystemPrompt("system prompt")
            .withTask("task")
            .addUserMessage("a".repeat(50))
            .addUserMessage("b".repeat(50))
            .addUserMessage("c".repeat(50));

    List<AgentMessage> messages = ctx.build();

    boolean hasCompressionSummary =
        messages.stream().anyMatch(m -> m.content() != null && m.content().contains("[上下文已压缩]"));
    assertThat(hasCompressionSummary).isTrue();
    assertThat(messages.get(0).role()).isEqualTo(AgentMessage.Role.SYSTEM);
    assertThat(messages.get(messages.size() - 1).role()).isEqualTo(AgentMessage.Role.USER);
  }

  @Test
  @DisplayName("可记录 assistant 文本与工具调用结果")
  void shouldRecordAssistantAndToolResult() {
    ContextManager ctx = new ContextManager().withTask("t");

    ctx.addAssistantMessage(ModelResponse.text("好的", TokenUsage.zero()));
    ctx.addToolResult(new ToolCall("c1", "sample", Map.of()), ToolResult.success("42"));

    List<AgentMessage> messages = ctx.build();

    assertThat(messages)
        .extracting(AgentMessage::role)
        .contains(AgentMessage.Role.ASSISTANT, AgentMessage.Role.TOOL);
  }

  @Test
  @DisplayName("currentTokenCount 非负且包含全部消息")
  void shouldReturnCurrentTokenCount() {
    ContextManager ctx = new ContextManager().withSystemPrompt("hello world").addUserMessage("hi");

    int count = ctx.currentTokenCount();
    assertThat(count).isGreaterThan(0);
  }
}
