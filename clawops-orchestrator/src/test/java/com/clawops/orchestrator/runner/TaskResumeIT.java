package com.clawops.orchestrator.runner;

import static org.assertj.core.api.Assertions.assertThat;

import com.clawops.harness.context.ContextConfig;
import com.clawops.harness.context.DefaultTokenCounter;
import com.clawops.harness.loop.AgentLoop;
import com.clawops.harness.loop.LoopConfig;
import com.clawops.harness.loop.LoopGuard;
import com.clawops.harness.permission.PermissionManager;
import com.clawops.harness.retry.RetryConfig;
import com.clawops.harness.retry.RetryExecutor;
import com.clawops.harness.spi.ModelClient;
import com.clawops.harness.spi.ModelResponse;
import com.clawops.harness.spi.Permission;
import com.clawops.harness.spi.TokenUsage;
import com.clawops.harness.spi.ToolCall;
import com.clawops.harness.spi.ToolDefinition;
import com.clawops.harness.tool.Tool;
import com.clawops.harness.tool.ToolRegistry;
import com.clawops.orchestrator.approval.ApprovalStatus;
import com.clawops.orchestrator.audit.JdbcAuditWriter;
import com.clawops.orchestrator.checkpoint.JdbcCheckpointStore;
import com.clawops.orchestrator.task.ContextSnapshotCodec;
import com.clawops.orchestrator.task.JpaTaskStore;
import com.clawops.orchestrator.task.OrchestratorTestApp;
import com.clawops.orchestrator.task.TaskEntityRepository;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 断点恢复集成测试：模拟进程死亡后重启续跑。
 *
 * <p>阶段 1：TaskRunner A 提交含 DANGEROUS 工具任务 → 暂停 + approval PENDING + context 快照落库。 丢弃 A 实例（模拟进程死亡，任务
 * PAUSED 残留库中）。 阶段 2：新建 TaskRunner B（同库）→ decide(approve) → resume → 续跑至 SUCCEEDED，task_step 全序保留。
 */
@Tag("it")
@Testcontainers
@EnabledIfDockerAvailable
@SpringBootTest(classes = OrchestratorTestApp.class)
@DisplayName("断点恢复：进程死亡后重启续跑（集成）")
class TaskResumeIT {

  @Container @ServiceConnection
  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("pgvector/pgvector:0.8.7-pg17-trixie")
          .withDatabaseName("clawops")
          .withUsername("clawops")
          .withPassword("clawops");

  @Autowired private JdbcTemplate jdbc;
  @Autowired private com.fasterxml.jackson.databind.ObjectMapper objectMapper;
  @Autowired private TaskEntityRepository repository;

  private StubModelClient modelClient;
  private RetryExecutor retryExecutor;
  private ToolRegistry toolRegistry;
  private JpaTaskStore taskStore;
  private JdbcCheckpointStore checkpointStore;
  private com.clawops.orchestrator.approval.JdbcApprovalStore approvalStore;
  private JdbcAuditWriter auditWriter;

  @BeforeEach
  void setUp() {
    modelClient = new StubModelClient();
    retryExecutor = new RetryExecutor();
    toolRegistry = new ToolRegistry().register(new DangerousTools());
    ContextSnapshotCodec codec = new ContextSnapshotCodec(objectMapper);
    taskStore = new JpaTaskStore(repository, objectMapper);
    checkpointStore = new JdbcCheckpointStore(jdbc, codec);
    approvalStore = new com.clawops.orchestrator.approval.JdbcApprovalStore(jdbc, codec);
    auditWriter = new JdbcAuditWriter(jdbc, codec);
  }

  @AfterEach
  void tearDown() {
    retryExecutor.close();
  }

  private TaskRunner newRunner() {
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
    return new TaskRunner(
        seed, taskStore, checkpointStore, approvalStore, auditWriter, toolRegistry, Runnable::run);
  }

  @Test
  @DisplayName("进程死亡后重启：新 TaskRunner decide(approve) 触发 resume 续跑成功")
  void shouldResumeAfterProcessRestart() {
    modelClient.enqueueToolCall(new ToolCall("c1", "restart", Map.of())); // 阶段1：暂停

    String taskId = newRunner().submit("重启服务");

    assertThat(taskStore.find(taskId).orElseThrow().status())
        .isEqualTo(com.clawops.harness.task.TaskState.PAUSED);
    assertThat(approvalStore.findPendingByTask(taskId)).hasSize(1);
    assertThat(checkpointStore.loadContext(taskId)).isNotEmpty();
    String approvalId = approvalStore.findPendingByTask(taskId).get(0).id();

    // 阶段 2：丢弃旧 runner，新建实例（模拟重启），enqueue 恢复阶段回复
    modelClient.enqueueToolCall(new ToolCall("c2", "restart", Map.of())); // 模型重新发起，gate 放行执行
    modelClient.enqueueText("已重启");

    newRunner().decide(approvalId, ApprovalStatus.APPROVED, "同意", "alice");

    assertThat(taskStore.find(taskId).orElseThrow().status())
        .isEqualTo(com.clawops.harness.task.TaskState.SUCCEEDED);
    assertThat(taskStore.find(taskId).orElseThrow().result()).isEqualTo("已重启");
    assertThat(checkpointStore.loadSteps(taskId)).anyMatch(s -> "restart".equals(s.toolName()));
    assertThat(approvalStore.findApprovedByTask(taskId)).hasSize(1);
  }

  static class DangerousTools {

    @Tool(name = "restart", description = "重启服务", permission = Permission.DANGEROUS)
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
    public ModelResponse chat(
        List<com.clawops.harness.spi.AgentMessage> messages, Collection<ToolDefinition> tools) {
      ModelResponse r = queue.poll();
      if (r == null) {
        throw new IllegalStateException("StubModelClient 无预设回复");
      }
      return r;
    }
  }
}
