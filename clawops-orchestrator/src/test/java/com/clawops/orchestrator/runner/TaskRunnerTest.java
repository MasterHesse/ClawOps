package com.clawops.orchestrator.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.clawops.harness.context.ContextConfig;
import com.clawops.harness.context.DefaultTokenCounter;
import com.clawops.harness.loop.AgentLoop;
import com.clawops.harness.loop.LoopConfig;
import com.clawops.harness.loop.LoopGuard;
import com.clawops.harness.permission.PermissionManager;
import com.clawops.harness.retry.RetryConfig;
import com.clawops.harness.retry.RetryExecutor;
import com.clawops.harness.spi.AgentMessage;
import com.clawops.harness.spi.ModelClient;
import com.clawops.harness.spi.ModelResponse;
import com.clawops.harness.spi.Permission;
import com.clawops.harness.spi.TokenUsage;
import com.clawops.harness.spi.ToolCall;
import com.clawops.harness.spi.ToolDefinition;
import com.clawops.harness.task.CheckpointStore;
import com.clawops.harness.task.StepCheckpoint;
import com.clawops.harness.task.TaskRecord;
import com.clawops.harness.task.TaskState;
import com.clawops.harness.task.TaskStore;
import com.clawops.harness.tool.ToolRegistry;
import com.clawops.orchestrator.approval.ApprovalRecord;
import com.clawops.orchestrator.approval.ApprovalStatus;
import com.clawops.orchestrator.approval.ApprovalStore;
import com.clawops.orchestrator.audit.JdbcAuditWriter;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** TaskRunner 编排单测（fake stores + 脚本化 ModelClient，conventions/testing.md §3 TDD）。 */
@DisplayName("TaskRunner 任务编排与断点恢复")
class TaskRunnerTest {

  private StubModelClient modelClient;
  private RetryExecutor retryExecutor;
  private ToolRegistry toolRegistry;
  private InMemoryTaskStore taskStore;
  private InMemoryCheckpointStore checkpointStore;
  private InMemoryApprovalStore approvalStore;
  private InMemoryAuditWriter auditWriter;
  private TaskRunner runner;

  @BeforeEach
  void setUp() {
    modelClient = new StubModelClient();
    retryExecutor = new RetryExecutor();
    toolRegistry = new ToolRegistry().register(new EchoTools());
    taskStore = new InMemoryTaskStore();
    checkpointStore = new InMemoryCheckpointStore();
    approvalStore = new InMemoryApprovalStore();
    auditWriter = new InMemoryAuditWriter();
    AgentLoop seed =
        new AgentLoop(
            modelClient,
            toolRegistry,
            new PermissionManager(),
            retryExecutor,
            new RetryConfig(1, 0, 100, 1000),
            new LoopGuard(2),
            new DefaultTokenCounter(),
            null,
            new LoopConfig(),
            new ContextConfig(1000, 0.8, 20));
    runner =
        new TaskRunner(
            seed,
            taskStore,
            checkpointStore,
            approvalStore,
            auditWriter,
            toolRegistry,
            Runnable::run); // 同步执行器，便于断言
  }

  @AfterEach
  void tearDown() {
    retryExecutor.close();
  }

  @Test
  @DisplayName("submit 完整流：工具步落 checkpoint，任务 SUCCEEDED")
  void shouldRunToSuccessWithCheckpoints() {
    modelClient.enqueueToolCall(new ToolCall("c1", "echo", Map.of("value", "ping")));
    modelClient.enqueueText("pong");

    String taskId = runner.submit("调用 echo");

    TaskRecord task = taskStore.find(taskId).orElseThrow();
    assertThat(task.status()).isEqualTo(TaskState.SUCCEEDED);
    assertThat(task.result()).isEqualTo("pong");
    assertThat(checkpointStore.loadSteps(taskId)).hasSize(1);
    assertThat(checkpointStore.loadContext(taskId)).isNotEmpty();
  }

  @Test
  @DisplayName("DANGEROUS 工具触发暂停并创建 PENDING 审批")
  void shouldPauseOnDangerousTool() {
    toolRegistry = new ToolRegistry().register(new DangerousTools());
    AgentLoop seed = newSeed();
    runner =
        new TaskRunner(
            seed,
            taskStore,
            checkpointStore,
            approvalStore,
            auditWriter,
            toolRegistry,
            Runnable::run);
    modelClient.enqueueToolCall(new ToolCall("c1", "restart", Map.of()));

    String taskId = runner.submit("重启服务");

    TaskRecord task = taskStore.find(taskId).orElseThrow();
    assertThat(task.status()).isEqualTo(TaskState.PAUSED);
    assertThat(task.pauseReason()).contains("审批");
    assertThat(approvalStore.findPendingByTask(taskId)).hasSize(1);
  }

  @Test
  @DisplayName("decide(approve) 恢复续跑至成功，已批准调用不再暂停")
  void shouldResumeAfterApproval() {
    toolRegistry = new ToolRegistry().register(new DangerousTools());
    AgentLoop seed = newSeed();
    runner =
        new TaskRunner(
            seed,
            taskStore,
            checkpointStore,
            approvalStore,
            auditWriter,
            toolRegistry,
            Runnable::run);
    modelClient.enqueueToolCall(new ToolCall("c1", "restart", Map.of())); // submit 阶段：暂停
    modelClient.enqueueToolCall(
        new ToolCall("c2", "restart", Map.of())); // resume 阶段：模型重新发起，gate 放行执行
    modelClient.enqueueText("已重启"); // 完成

    String taskId = runner.submit("重启服务");
    String approvalId = approvalStore.findPendingByTask(taskId).get(0).id();

    runner.decide(approvalId, ApprovalStatus.APPROVED, "同意", "alice");

    TaskRecord task = taskStore.find(taskId).orElseThrow();
    assertThat(task.status()).isEqualTo(TaskState.SUCCEEDED);
    assertThat(task.result()).isEqualTo("已重启");
    assertThat(approvalStore.findApprovedByTask(taskId)).hasSize(1);
    // 重启工具实际执行（步骤落盘）
    assertThat(checkpointStore.loadSteps(taskId)).anyMatch(s -> "restart".equals(s.toolName()));
  }

  @Test
  @DisplayName("decide(reject) 终结任务为 FAILED")
  void shouldFailOnRejection() {
    toolRegistry = new ToolRegistry().register(new DangerousTools());
    AgentLoop seed = newSeed();
    runner =
        new TaskRunner(
            seed,
            taskStore,
            checkpointStore,
            approvalStore,
            auditWriter,
            toolRegistry,
            Runnable::run);
    modelClient.enqueueToolCall(new ToolCall("c1", "restart", Map.of()));

    String taskId = runner.submit("重启");
    String approvalId = approvalStore.findPendingByTask(taskId).get(0).id();

    runner.decide(approvalId, ApprovalStatus.REJECTED, "拒绝", "bob");

    assertThat(taskStore.find(taskId).orElseThrow().status()).isEqualTo(TaskState.FAILED);
  }

  @Test
  @DisplayName("非 PAUSED 状态 resume 抛 TaskRunnerException")
  void shouldRejectResumeWhenNotPaused() {
    String taskId =
        taskStore.create(TaskRecord.create(UUID.randomUUID().toString(), "p", Instant.now())).id();
    assertThatThrownBy(() -> runner.resume(taskId)).isInstanceOf(TaskRunnerException.class);
  }

  private AgentLoop newSeed() {
    return new AgentLoop(
        modelClient,
        toolRegistry,
        new PermissionManager(),
        retryExecutor,
        new RetryConfig(1, 0, 100, 1000),
        new LoopGuard(2),
        new DefaultTokenCounter(),
        null,
        new LoopConfig(),
        new ContextConfig(1000, 0.8, 20));
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

  static class StubModelClient implements ModelClient {

    private final Deque<ModelResponse> queue = new ArrayDeque<>();

    void enqueueText(String text) {
      queue.add(ModelResponse.text(text, new TokenUsage(1, 1, 2)));
    }

    void enqueueToolCall(ToolCall call) {
      queue.add(ModelResponse.toolCalls(List.of(call), new TokenUsage(2, 1, 3)));
    }

    @Override
    public ModelResponse chat(List<AgentMessage> messages, Collection<ToolDefinition> tools) {
      ModelResponse r = queue.poll();
      if (r == null) {
        throw new IllegalStateException("StubModelClient 无预设回复");
      }
      return r;
    }
  }

  static class InMemoryTaskStore implements TaskStore {

    final Map<String, TaskRecord> store = new HashMap<>();

    @Override
    public TaskRecord create(TaskRecord task) {
      store.put(task.id(), task);
      return task;
    }

    @Override
    public Optional<TaskRecord> find(String taskId) {
      return Optional.ofNullable(store.get(taskId));
    }

    @Override
    public List<TaskRecord> findByStates(TaskState... states) {
      return store.values().stream().filter(t -> List.of(states).contains(t.status())).toList();
    }

    @Override
    public TaskRecord update(TaskRecord task) {
      store.put(task.id(), task);
      return task;
    }
  }

  static class InMemoryCheckpointStore implements CheckpointStore {

    final Map<String, List<StepCheckpoint>> steps = new HashMap<>();
    final Map<String, List<AgentMessage>> contexts = new HashMap<>();

    @Override
    public void saveStep(StepCheckpoint checkpoint) {
      steps.computeIfAbsent(checkpoint.taskId(), k -> new ArrayList<>()).add(checkpoint);
    }

    @Override
    public List<StepCheckpoint> loadSteps(String taskId) {
      return steps.getOrDefault(taskId, List.of());
    }

    @Override
    public void saveContext(String taskId, List<AgentMessage> context) {
      contexts.put(taskId, List.copyOf(context));
    }

    @Override
    public List<AgentMessage> loadContext(String taskId) {
      return contexts.getOrDefault(taskId, List.of());
    }
  }

  static class InMemoryApprovalStore implements ApprovalStore {

    final Map<String, ApprovalRecord> store = new HashMap<>();

    @Override
    public ApprovalRecord create(ApprovalRecord approval) {
      store.put(approval.id(), approval);
      return approval;
    }

    @Override
    public Optional<ApprovalRecord> find(String approvalId) {
      return Optional.ofNullable(store.get(approvalId));
    }

    @Override
    public List<ApprovalRecord> findPendingByTask(String taskId) {
      return store.values().stream()
          .filter(a -> a.taskId().equals(taskId) && a.status() == ApprovalStatus.PENDING)
          .toList();
    }

    @Override
    public List<ApprovalRecord> findApprovedByTask(String taskId) {
      return store.values().stream()
          .filter(a -> a.taskId().equals(taskId) && a.status() == ApprovalStatus.APPROVED)
          .toList();
    }

    @Override
    public ApprovalRecord decide(
        String approvalId, ApprovalStatus decision, String reason, String decidedBy) {
      ApprovalRecord current = store.get(approvalId);
      ApprovalRecord next = current.withDecision(decision, reason, decidedBy, Instant.now());
      store.put(approvalId, next);
      return next;
    }
  }

  static class InMemoryAuditWriter extends JdbcAuditWriter {

    InMemoryAuditWriter() {
      super(null, null);
    }

    @Override
    public void write(String taskId, String actor, String action, Map<String, Object> detail) {
      // no-op
    }
  }
}
