package com.clawops.harness.loop;

import static org.assertj.core.api.Assertions.assertThat;

import com.clawops.harness.context.ContextConfig;
import com.clawops.harness.context.DefaultTokenCounter;
import com.clawops.harness.permission.PermissionManager;
import com.clawops.harness.retry.RetryConfig;
import com.clawops.harness.retry.RetryExecutor;
import com.clawops.harness.spi.AgentEventListener;
import com.clawops.harness.spi.AgentMessage;
import com.clawops.harness.spi.Permission;
import com.clawops.harness.spi.ToolCall;
import com.clawops.harness.spi.ToolResult;
import com.clawops.harness.task.ApprovalGate;
import com.clawops.harness.tool.ToolRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** AgentLoop 审批暂停与断点恢复（conventions/testing.md §3 TDD 强制范围）。 */
@DisplayName("AgentLoop 审批暂停与恢复")
class AgentLoopApprovalTest {

  private StubModelClient modelClient;
  private RetryExecutor retryExecutor;
  private ToolRegistry toolRegistry;
  private PermissionManager permissionManager;
  private RecordingListener listener;
  private ScriptedGate gate;

  @BeforeEach
  void setUp() {
    modelClient = new StubModelClient();
    retryExecutor = new RetryExecutor();
    toolRegistry = new ToolRegistry().register(new AgentLoopTest.DangerousTools());
    permissionManager = new PermissionManager();
    listener = new RecordingListener();
    gate = new ScriptedGate();
  }

  @AfterEach
  void tearDown() {
    retryExecutor.close();
  }

  private AgentLoop createLoop() {
    return new AgentLoop(
        modelClient,
        toolRegistry,
        permissionManager,
        retryExecutor,
        new RetryConfig(1, 0, 100, 1000),
        new LoopGuard(2),
        new DefaultTokenCounter(),
        listener,
        new LoopConfig(),
        new ContextConfig(1000, 0.8, 20));
  }

  @Test
  @DisplayName("gate 要求审批时返回 PAUSED_FOR_APPROVAL 并携带 pendingToolCall 与 approvalId")
  void shouldPauseWhenGateRequiresApproval() {
    gate.requireApprovalFor("restart", "approval-1");
    modelClient.enqueueToolCall(new ToolCall("c1", "restart", Map.of()));

    LoopResult result = createLoop().withApprovalGate(gate).run("重启服务");

    assertThat(result.status()).isEqualTo(LoopResult.Status.PAUSED_FOR_APPROVAL);
    assertThat(result.pendingToolCall().name()).isEqualTo("restart");
    assertThat(result.approvalId()).isEqualTo("approval-1");
    assertThat(listener.approvalRequiredCalls).hasSize(1);
    assertThat(listener.approvalRequiredCalls.get(0).call().name()).isEqualTo("restart");
    assertThat(listener.doneCalled).isFalse();
  }

  @Test
  @DisplayName("gate 放行时工具正常执行")
  void shouldExecuteWhenGateAllows() {
    toolRegistry = new ToolRegistry().register(new AgentLoopTest.EchoTools());
    permissionManager = new PermissionManager(Permission.DANGEROUS);
    modelClient.enqueueToolCall(new ToolCall("c1", "echo", Map.of("value", "hi")));
    modelClient.enqueueText("done");

    LoopResult result = createLoop().withApprovalGate(gate).run("调用 echo");

    assertThat(result.status()).isEqualTo(LoopResult.Status.SUCCESS);
    assertThat(listener.toolResults).hasSize(1);
    assertThat(listener.toolResults.get(0).isSuccess()).isTrue();
  }

  @Test
  @DisplayName("gate 为 null 时保持旧行为：DANGEROUS 工具被 PermissionManager 拒绝")
  void shouldKeepForbiddenBehaviorWithoutGate() {
    modelClient.enqueueToolCall(new ToolCall("c1", "restart", Map.of()));
    modelClient.enqueueText("跳过");

    LoopResult result = createLoop().run("重启");

    assertThat(result.status()).isEqualTo(LoopResult.Status.SUCCESS);
    assertThat(listener.toolResults.get(0).status())
        .isEqualTo(com.clawops.harness.spi.ToolResult.STATUS_FORBIDDEN);
  }

  @Test
  @DisplayName("run(task, history) 恢复时模型收到注入的历史消息")
  void shouldReceiveHistoryOnResume() {
    List<AgentMessage> history =
        List.of(AgentMessage.user("查 CPU"), AgentMessage.assistant("已分析指标"));
    modelClient.enqueueText("继续完成");

    LoopResult result = createLoop().run("继续", history);

    assertThat(result.status()).isEqualTo(LoopResult.Status.SUCCESS);
    assertThat(modelClient.lastRequests.get(0))
        .extracting(AgentMessage::content)
        .contains("已分析指标", "继续");
  }

  static class ScriptedGate implements ApprovalGate {

    final Map<String, String> requireMap = new HashMap<>();

    void requireApprovalFor(String toolName, String approvalId) {
      requireMap.put(toolName, approvalId);
    }

    @Override
    public Optional<String> requireApproval(ToolCall call) {
      return Optional.ofNullable(requireMap.get(call.name()));
    }
  }

  static class RecordingListener implements AgentEventListener {

    final List<ApprovalRequiredEvent> approvalRequiredCalls = new ArrayList<>();
    final List<ToolResult> toolResults = new ArrayList<>();
    boolean doneCalled = false;

    @Override
    public void onToolResult(String toolCallId, ToolResult result) {
      toolResults.add(result);
    }

    @Override
    public void onDone(String answer, int steps, com.clawops.harness.spi.TokenUsage usage) {
      doneCalled = true;
    }

    @Override
    public void onApprovalRequired(ToolCall call, String approvalId) {
      approvalRequiredCalls.add(new ApprovalRequiredEvent(call, approvalId));
    }
  }

  record ApprovalRequiredEvent(ToolCall call, String approvalId) {}
}
