package com.clawops.orchestrator.runner;

import com.clawops.harness.loop.AgentLoop;
import com.clawops.harness.loop.LoopResult;
import com.clawops.harness.permission.PermissionManager;
import com.clawops.harness.spi.AgentEventListener;
import com.clawops.harness.spi.AgentMessage;
import com.clawops.harness.spi.Permission;
import com.clawops.harness.spi.TokenUsage;
import com.clawops.harness.spi.ToolCall;
import com.clawops.harness.spi.ToolResult;
import com.clawops.harness.task.CheckpointStore;
import com.clawops.harness.task.StepCheckpoint;
import com.clawops.harness.task.TaskRecord;
import com.clawops.harness.task.TaskState;
import com.clawops.harness.task.TaskStore;
import com.clawops.harness.tool.ToolRegistry;
import com.clawops.orchestrator.approval.ApprovalStatus;
import com.clawops.orchestrator.approval.ApprovalStore;
import com.clawops.orchestrator.audit.JdbcAuditWriter;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * 可恢复任务运行器：编排任务生命周期（submit / 异步 run / 事件落检查点 / DANGEROUS 暂停写审批 / decide 触发 resume）。
 *
 * <p>每任务独立 {@link PermissionManager} 与 {@link TaskApprovalGate}；恢复时从 {@link
 * CheckpointStore#loadContext} 重建历史， 已批准的 DANGEROUS 调用由 gate 放行、manager 提升至 DANGEROUS 后执行。详见
 * ADR-008。
 */
public class TaskRunner {

  private final AgentLoop seed;
  private final TaskStore taskStore;
  private final CheckpointStore checkpointStore;
  private final ApprovalStore approvalStore;
  private final JdbcAuditWriter auditWriter;
  private final ToolRegistry toolRegistry;
  private final Executor executor;

  public TaskRunner(
      AgentLoop seed,
      TaskStore taskStore,
      CheckpointStore checkpointStore,
      ApprovalStore approvalStore,
      JdbcAuditWriter auditWriter,
      ToolRegistry toolRegistry,
      Executor executor) {
    this.seed = seed;
    this.taskStore = taskStore;
    this.checkpointStore = checkpointStore;
    this.approvalStore = approvalStore;
    this.auditWriter = auditWriter;
    this.toolRegistry = toolRegistry;
    this.executor = executor;
  }

  /** 提交任务，返回 taskId（异步执行）。 */
  public String submit(String prompt) {
    String taskId = UUID.randomUUID().toString();
    TaskRecord task = TaskRecord.create(taskId, prompt, Instant.now());
    taskStore.create(task);
    taskStore.update(task.withStatus(TaskState.RUNNING, 0));
    auditWriter.write(taskId, "agent", "task.start", Map.of("prompt", prompt));
    executor.execute(() -> doRun(taskId, prompt, List.of()));
    return taskId;
  }

  /** 恢复暂停的任务（幂等校验：仅 PAUSED 可恢复）。 */
  public void resume(String taskId) {
    TaskRecord task =
        taskStore.find(taskId).orElseThrow(() -> new TaskRunnerException("任务不存在: " + taskId));
    if (task.status() != TaskState.PAUSED) {
      throw new TaskRunnerException("任务非暂停状态，无法恢复: " + task.status());
    }
    taskStore.update(task.withStatus(TaskState.RUNNING, task.steps()));
    auditWriter.write(taskId, "agent", "task.resume", Map.of("steps", task.steps()));
    List<AgentMessage> resumed = checkpointStore.loadContext(taskId);
    executor.execute(() -> doRun(taskId, task.prompt(), resumed));
  }

  /** 应用审批决策：approve 触发 resume；reject 终结任务为 FAILED。 */
  public void decide(String approvalId, ApprovalStatus decision, String reason, String decidedBy) {
    String taskId =
        approvalStore
            .find(approvalId)
            .orElseThrow(() -> new TaskRunnerException("审批单不存在: " + approvalId))
            .taskId();
    approvalStore.decide(approvalId, decision, reason, decidedBy);
    auditWriter.write(
        taskId,
        decidedBy,
        "approval.decide",
        Map.of("approvalId", approvalId, "decision", decision.name(), "reason", reason));
    if (decision == ApprovalStatus.APPROVED) {
      resume(taskId);
    } else {
      TaskRecord task =
          taskStore.find(taskId).orElseThrow(() -> new TaskRunnerException("任务不存在: " + taskId));
      taskStore.update(
          task.fail("审批拒绝: " + reason, task.steps(), task.tokenUsage(), task.contextMessages()));
    }
  }

  private void doRun(String taskId, String prompt, List<AgentMessage> resumed) {
    try {
      PermissionManager manager = new PermissionManager();
      if (!resumed.isEmpty()) {
        manager.elevate(Permission.DANGEROUS); // 恢复时已通过审批，允许 DANGEROUS
      }
      TaskApprovalGate gate = new TaskApprovalGate(approvalStore, toolRegistry, taskId);
      TaskListener listener = new TaskListener(taskId, checkpointStore, auditWriter);
      AgentLoop loop =
          seed.withPermissionManager(manager).withApprovalGate(gate).withListener(listener);
      LoopResult result = loop.run(prompt, resumed);
      List<AgentMessage> snapshot = loop.contextSnapshot();
      checkpointStore.saveContext(taskId, snapshot);
      applyResult(taskId, result, snapshot);
    } catch (Exception e) {
      TaskRecord task = taskStore.find(taskId).orElse(null);
      if (task != null && !task.status().isTerminal()) {
        taskStore.update(
            task.fail("运行异常: " + e.getMessage(), task.steps(), TokenUsage.zero(), List.of()));
      }
    }
  }

  private void applyResult(String taskId, LoopResult result, List<AgentMessage> context) {
    TaskRecord task =
        taskStore.find(taskId).orElseThrow(() -> new TaskRunnerException("任务不存在: " + taskId));
    switch (result.status()) {
      case SUCCESS -> {
        taskStore.update(
            task.complete(result.answer(), result.steps(), result.tokenUsage(), context));
        auditWriter.write(taskId, "agent", "task.done", Map.of("answer", result.answer()));
      }
      case PAUSED_FOR_APPROVAL -> {
        taskStore.update(task.pause("等待审批: " + result.approvalId()));
        auditWriter.write(
            taskId,
            "agent",
            "task.paused",
            Map.of(
                "approvalId", result.approvalId(),
                "tool", result.pendingToolCall().name()));
      }
      default -> {
        taskStore.update(task.fail(result.answer(), result.steps(), result.tokenUsage(), context));
        auditWriter.write(taskId, "agent", "task.failed", Map.of("reason", result.answer()));
      }
    }
  }

  /** 任务事件监听器：每步工具结果落 StepCheckpoint。 */
  private static final class TaskListener implements AgentEventListener {

    private final String taskId;
    private final CheckpointStore checkpointStore;
    private final JdbcAuditWriter auditWriter;
    private final Map<String, ToolCall> pendingCalls = new HashMap<>();
    private int seq = 0;

    TaskListener(String taskId, CheckpointStore checkpointStore, JdbcAuditWriter auditWriter) {
      this.taskId = taskId;
      this.checkpointStore = checkpointStore;
      this.auditWriter = auditWriter;
    }

    @Override
    public void onToolCall(ToolCall call) {
      pendingCalls.put(call.id(), call);
    }

    @Override
    public void onToolResult(String toolCallId, ToolResult result) {
      ToolCall call = pendingCalls.get(toolCallId);
      if (call == null) {
        return;
      }
      StepCheckpoint step =
          new StepCheckpoint(
              taskId,
              ++seq,
              call.id(),
              call.name(),
              call.arguments(),
              result.status(),
              summarize(result.content()),
              Instant.now());
      checkpointStore.saveStep(step);
      auditWriter.write(
          taskId,
          "agent",
          "tool.call",
          Map.of(
              "tool", call.name(),
              "status", result.status(),
              "seq", seq));
    }

    private static String summarize(String content) {
      if (content == null) {
        return "";
      }
      return content.length() > 2000 ? content.substring(0, 2000) + "...(truncated)" : content;
    }
  }
}
