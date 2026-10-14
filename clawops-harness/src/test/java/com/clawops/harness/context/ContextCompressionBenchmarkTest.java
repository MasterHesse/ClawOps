package com.clawops.harness.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.clawops.harness.spi.AgentMessage;
import com.clawops.harness.spi.Permission;
import com.clawops.harness.spi.TokenCounter;
import com.clawops.harness.spi.ToolCall;
import com.clawops.harness.spi.ToolDefinition;
import com.clawops.harness.spi.ToolResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 上下文压缩效果基准测试。
 *
 * <p>结果写入 docs/metrics/w1-context-compression.md。
 */
@DisplayName("上下文压缩效果基准")
class ContextCompressionBenchmarkTest {

  private static final TokenCounter CHAR_COUNTER = text -> text == null ? 0 : text.length();
  private static final ToolDefinition DUMMY_TOOL =
      new ToolDefinition("dummy", "", Permission.READ, Map.of(), call -> null, null);

  @Test
  @DisplayName("压缩能显著降低长对话的 token 占用")
  void shouldReduceTokenUsageWithCompression() {
    int uncompressedTokens = buildContext(false);
    int compressedTokens = buildContext(true);

    System.out.println("未压缩 token 数: " + uncompressedTokens);
    System.out.println("压缩后 token 数: " + compressedTokens);
    System.out.println(
        "压缩比: " + String.format("%.2f", (double) compressedTokens / uncompressedTokens));

    assertThat(compressedTokens).isLessThan(uncompressedTokens);
  }

  private int buildContext(boolean enableCompression) {
    ContextConfig config;
    if (enableCompression) {
      config = new ContextConfig(600, 0.8, 40);
    } else {
      config = new ContextConfig(200000, 0.8, 40);
    }
    ContextManager ctx =
        new ContextManager(config, CHAR_COUNTER, new WindowCompressionStrategy())
            .withSystemPrompt("你是 ClawOps 运维助手。")
            .withRolePrompt("使用工具诊断问题。")
            .withTools(List.of(DUMMY_TOOL))
            .withTask("排查 inventory-service 异常");

    for (int i = 0; i < 20; i++) {
      String detail =
          "第 " + i + " 轮巡检：连接池活跃数=" + i + "，等待队列长度=" + (i * 5) + "，线程栈显示大量线程阻塞在 getConnection。";
      ctx.addUserMessage("查询第 " + i + " 次");
      ctx.addAssistantMessage(
          com.clawops.harness.spi.ModelResponse.toolCalls(
              List.of(new ToolCall("c" + i, "dummy", Map.of("i", i))),
              com.clawops.harness.spi.TokenUsage.zero()));
      ctx.addToolResult(new ToolCall("c" + i, "dummy", Map.of("i", i)), ToolResult.success(detail));
    }

    List<AgentMessage> messages = ctx.build();
    return CHAR_COUNTER.countMessages(messages);
  }
}
