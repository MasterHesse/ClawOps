package com.clawops.harness.loop;

import static org.assertj.core.api.Assertions.assertThat;

import com.clawops.harness.context.ContextConfig;
import com.clawops.harness.context.DefaultTokenCounter;
import com.clawops.harness.permission.PermissionManager;
import com.clawops.harness.retry.RetryConfig;
import com.clawops.harness.retry.RetryExecutor;
import com.clawops.harness.spi.AgentEventListener;
import com.clawops.harness.spi.Permission;
import com.clawops.harness.spi.TokenUsage;
import com.clawops.harness.spi.ToolCall;
import com.clawops.harness.spi.ToolResult;
import com.clawops.harness.tool.ToolRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AgentLoop 感知-推理-行动-反馈循环")
class AgentLoopTest {

  private StubModelClient modelClient;
  private RetryExecutor retryExecutor;
  private ToolRegistry toolRegistry;
  private PermissionManager permissionManager;
  private RecordingListener listener;

  @BeforeEach
  void setUp() {
    modelClient = new StubModelClient();
    retryExecutor = new RetryExecutor();
    toolRegistry = new ToolRegistry().register(new EchoTools());
    permissionManager = new PermissionManager();
    listener = new RecordingListener();
  }

  @AfterEach
  void tearDown() {
    retryExecutor.close();
  }

  private AgentLoop createLoop(LoopConfig config) {
    return new AgentLoop(
        modelClient,
        toolRegistry,
        permissionManager,
        retryExecutor,
        new RetryConfig(1, 0, 100, 1000),
        new LoopGuard(2),
        new DefaultTokenCounter(),
        listener,
        config,
        new ContextConfig(1000, 0.8, 20));
  }

  @Test
  @DisplayName("模型直接返回文本时结束并触发 onDone")
  void shouldReturnTextAnswer() {
    modelClient.enqueueText("已完成");

    AgentLoop loop = createLoop(new LoopConfig());
    LoopResult result = loop.run("诊断问题");

    assertThat(result.status()).isEqualTo(LoopResult.Status.SUCCESS);
    assertThat(result.answer()).isEqualTo("已完成");
    assertThat(listener.deltas).containsExactly("已完成");
    assertThat(listener.doneCalled).isTrue();
  }

  @Test
  @DisplayName("模型调用工具后将结果回填上下文")
  void shouldExecuteToolAndContinue() {
    modelClient.enqueueToolCall(new ToolCall("c1", "echo", Map.of("value", "ping")));
    modelClient.enqueueText("收到 pong");

    AgentLoop loop = createLoop(new LoopConfig());
    LoopResult result = loop.run("调用 echo");

    assertThat(result.status()).isEqualTo(LoopResult.Status.SUCCESS);
    assertThat(result.answer()).isEqualTo("收到 pong");
    assertThat(listener.toolCalls).hasSize(1);
    assertThat(listener.toolResults).hasSize(1);
    assertThat(listener.toolResults.get(0).status()).isEqualTo(ToolResult.STATUS_SUCCESS);
  }

  @Test
  @DisplayName("DANGEROUS 工具默认被拦截")
  void shouldBlockDangerousTool() {
    toolRegistry = new ToolRegistry().register(new DangerousTools());
    modelClient.enqueueToolCall(new ToolCall("c1", "restart", Map.of()));
    modelClient.enqueueText("已跳过");

    AgentLoop loop = createLoop(new LoopConfig());
    LoopResult result = loop.run("重启服务");

    assertThat(result.status()).isEqualTo(LoopResult.Status.SUCCESS);
    assertThat(listener.toolResults.get(0).status()).isEqualTo(ToolResult.STATUS_FORBIDDEN);
  }

  @Test
  @DisplayName("重复工具调用触发死循环保护")
  void shouldDetectLoop() {
    modelClient.enqueueToolCall(new ToolCall("c1", "echo", Map.of("value", "x")));
    modelClient.enqueueToolCall(new ToolCall("c2", "echo", Map.of("value", "x")));
    modelClient.enqueueToolCall(new ToolCall("c3", "echo", Map.of("value", "x")));

    AgentLoop loop = createLoop(new LoopConfig());
    LoopResult result = loop.run("循环调用");

    assertThat(result.status()).isEqualTo(LoopResult.Status.LOOP_DETECTED);
    assertThat(result.answer()).contains("死循环");
  }

  @Test
  @DisplayName("达到最大步数后返回 MAX_STEPS")
  void shouldStopAtMaxSteps() {
    for (int i = 0; i < 5; i++) {
      modelClient.enqueueToolCall(new ToolCall("c" + i, "echo", Map.of("value", i)));
    }

    AgentLoop loop = createLoop(new LoopConfig(3, 60, "", ""));
    LoopResult result = loop.run("多步调用");

    assertThat(result.status()).isEqualTo(LoopResult.Status.MAX_STEPS);
  }

  static class EchoTools {

    @com.clawops.harness.tool.Tool(description = "回显", permission = Permission.READ)
    public String echo(@com.clawops.harness.tool.ToolParam(name = "value") String value) {
      return value;
    }
  }

  static class DangerousTools {

    @com.clawops.harness.tool.Tool(
        name = "restart",
        description = "重启服务",
        permission = Permission.DANGEROUS)
    public String restart() {
      return "restarted";
    }
  }

  static class RecordingListener implements AgentEventListener {

    final List<String> deltas = new ArrayList<>();
    final List<ToolCall> toolCalls = new ArrayList<>();
    final List<ToolResult> toolResults = new ArrayList<>();
    boolean doneCalled = false;

    @Override
    public void onDelta(String text) {
      deltas.add(text);
    }

    @Override
    public void onToolCall(ToolCall call) {
      toolCalls.add(call);
    }

    @Override
    public void onToolResult(String toolCallId, ToolResult result) {
      toolResults.add(result);
    }

    @Override
    public void onDone(String answer, int steps, TokenUsage usage) {
      doneCalled = true;
    }
  }
}
