package com.clawops.orchestrator.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.clawops.harness.spi.AgentMessage;
import com.clawops.harness.spi.TokenUsage;
import com.clawops.harness.task.TaskRecord;
import com.clawops.harness.task.TaskState;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;

/** JpaTaskStore 任务落库集成测试（@SpringBootTest + Testcontainers pgvector，走 Flyway V1 迁移）。 */
@Tag("it")
@Testcontainers
@EnabledIfDockerAvailable
@SpringBootTest(classes = OrchestratorTestApp.class)
@DisplayName("JpaTaskStore 任务落库（集成）")
class JpaTaskStoreIT {

  @Container @ServiceConnection
  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("pgvector/pgvector:0.8.7-pg17-trixie")
          .withDatabaseName("clawops")
          .withUsername("clawops")
          .withPassword("clawops");

  @Autowired private TaskEntityRepository repository;
  @Autowired private com.fasterxml.jackson.databind.ObjectMapper objectMapper;
  private JpaTaskStore store;

  @BeforeEach
  void setUp() {
    store = new JpaTaskStore(repository, objectMapper);
  }

  @Test
  @DisplayName("create 后 find 应返回相同字段（含 uuid 主键）")
  void shouldCreateAndFind() {
    String id = UUID.randomUUID().toString();
    Instant now = Instant.now();
    TaskRecord created = store.create(TaskRecord.create(id, "诊断 CPU 飙高", now));

    assertThat(created.id()).isEqualTo(id);
    assertThat(created.status()).isEqualTo(TaskState.CREATED);

    TaskRecord found = store.find(id).orElseThrow();
    assertThat(found.prompt()).isEqualTo("诊断 CPU 飙高");
    assertThat(found.status()).isEqualTo(TaskState.CREATED);
    assertThat(found.steps()).isZero();
    assertThat(found.tokenUsage()).isEqualTo(TokenUsage.zero());
    assertThat(found.contextMessages()).isEmpty();
  }

  @Test
  @DisplayName("update 迁移状态时 tokenUsage 与 contextMessages 的 jsonb 往返")
  void shouldUpdateWithJsonbRoundTrip() {
    String id = UUID.randomUUID().toString();
    TaskRecord created = store.create(TaskRecord.create(id, "p", Instant.now()));
    TaskRecord running = store.update(created.withStatus(TaskState.RUNNING, 0));
    TokenUsage usage = new TokenUsage(100, 50, 150);
    List<AgentMessage> context = List.of(AgentMessage.user("诊断"), AgentMessage.assistant("已分析"));

    TaskRecord updated = store.update(running.complete("根因：连接池耗尽", 3, usage, context));

    assertThat(updated.status()).isEqualTo(TaskState.SUCCEEDED);
    assertThat(updated.tokenUsage()).isEqualTo(usage);
    assertThat(updated.contextMessages()).hasSize(2);

    TaskRecord found = store.find(id).orElseThrow();
    assertThat(found.tokenUsage()).isEqualTo(usage);
    assertThat(found.contextMessages()).extracting(AgentMessage::content).contains("诊断", "已分析");
  }

  @Test
  @DisplayName("findByStates 按状态过滤任务")
  void shouldFindByStates() {
    String a = UUID.randomUUID().toString();
    String b = UUID.randomUUID().toString();
    String c = UUID.randomUUID().toString();
    store.create(TaskRecord.create(a, "p1", Instant.now()).withStatus(TaskState.RUNNING, 0));
    store.create(
        TaskRecord.create(b, "p2", Instant.now()).withStatus(TaskState.RUNNING, 0).pause("待恢复"));
    store.create(TaskRecord.create(c, "p3", Instant.now()));

    List<TaskRecord> resumable = store.findByStates(TaskState.RUNNING, TaskState.PAUSED);
    assertThat(resumable).extracting(TaskRecord::id).containsExactlyInAnyOrder(a, b);
  }

  @Test
  @DisplayName("非法状态迁移抛 IllegalStateException")
  void shouldRejectIllegalTransition() {
    String id = UUID.randomUUID().toString();
    TaskRecord task = store.create(TaskRecord.create(id, "p", Instant.now()));
    assertThatThrownBy(() -> store.update(task.withStatus(TaskState.SUCCEEDED, 0)))
        .isInstanceOf(IllegalStateException.class);
  }
}
