package com.clawops.orchestrator.checkpoint;

import static org.assertj.core.api.Assertions.assertThat;

import com.clawops.harness.spi.AgentMessage;
import com.clawops.harness.spi.ToolCall;
import com.clawops.harness.task.StepCheckpoint;
import com.clawops.harness.task.TaskRecord;
import com.clawops.orchestrator.task.ContextSnapshotCodec;
import com.clawops.orchestrator.task.JpaTaskStore;
import com.clawops.orchestrator.task.OrchestratorTestApp;
import com.clawops.orchestrator.task.TaskEntityRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;

/** JdbcCheckpointStore 检查点落库集成测试。 */
@Tag("it")
@Testcontainers
@EnabledIfDockerAvailable
@SpringBootTest(classes = OrchestratorTestApp.class)
@DisplayName("JdbcCheckpointStore 检查点落库（集成）")
class JdbcCheckpointStoreIT {

  @Container @ServiceConnection
  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("pgvector/pgvector:0.8.7-pg17-trixie")
          .withDatabaseName("clawops")
          .withUsername("clawops")
          .withPassword("clawops");

  @Autowired private JdbcTemplate jdbc;
  @Autowired private com.fasterxml.jackson.databind.ObjectMapper objectMapper;
  @Autowired private TaskEntityRepository repository;

  private JdbcCheckpointStore store;
  private JpaTaskStore taskStore;
  private String taskId;

  @BeforeEach
  void setUp() {
    ContextSnapshotCodec codec = new ContextSnapshotCodec(objectMapper);
    store = new JdbcCheckpointStore(jdbc, codec);
    taskStore = new JpaTaskStore(repository, objectMapper);
    taskId = UUID.randomUUID().toString();
    taskStore.create(TaskRecord.create(taskId, "诊断任务", Instant.now()));
  }

  @Test
  @DisplayName("saveStep 落 3 步后 loadSteps 按 seq 升序返回完整字段")
  void shouldSaveAndLoadStepsInOrder() {
    Instant now = Instant.now();
    store.saveStep(
        new StepCheckpoint(
            taskId, 1, "call-1", "metrics.query", Map.of("q", "up"), "success", "up=1", now));
    store.saveStep(
        new StepCheckpoint(
            taskId, 2, "call-2", "log.search", Map.of("level", "ERROR"), "success", "err", now));
    store.saveStep(
        new StepCheckpoint(
            taskId, 3, "call-3", "log.search", Map.of("level", "WARN"), "degraded", "warn", now));

    List<StepCheckpoint> steps = store.loadSteps(taskId);
    assertThat(steps).hasSize(3);
    assertThat(steps).extracting(StepCheckpoint::seq).containsExactly(1, 2, 3);
    assertThat(steps.get(0).toolName()).isEqualTo("metrics.query");
    assertThat(steps.get(0).arguments()).containsEntry("q", "up");
    assertThat(steps.get(2).resultStatus()).isEqualTo("degraded");
  }

  @Test
  @DisplayName("saveContext 与 loadContext 全量快照 round-trip（含工具调用消息）")
  void shouldSaveAndLoadContextSnapshot() {
    ToolCall call = new ToolCall("call-1", "metrics.query", Map.of("q", "up"));
    List<AgentMessage> context =
        List.of(
            AgentMessage.user("查 CPU"),
            AgentMessage.assistant(call),
            AgentMessage.tool("call-1", "[metrics.query] up=1"));

    store.saveContext(taskId, context);

    List<AgentMessage> restored = store.loadContext(taskId);
    assertThat(restored).hasSize(3);
    assertThat(restored.get(1).hasToolCalls()).isTrue();
    assertThat(restored.get(1).toolCalls().get(0).arguments()).containsEntry("q", "up");
    assertThat(restored.get(2).toolCallId()).isEqualTo("call-1");
  }

  @Test
  @DisplayName("saveStep 的 taskId 必须已存在（外键约束）")
  void shouldEnforceForeignKey() {
    assertThat(
            org.assertj.core.api.Assertions.catchThrowable(
                () ->
                    store.saveStep(
                        new StepCheckpoint(
                            UUID.randomUUID().toString(),
                            1,
                            "call-x",
                            "metrics.query",
                            Map.of(),
                            "success",
                            "x",
                            Instant.now()))))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
