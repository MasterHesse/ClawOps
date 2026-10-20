package com.clawops.orchestrator.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.clawops.harness.spi.Permission;
import com.clawops.harness.spi.ToolCall;
import com.clawops.harness.task.TaskRecord;
import com.clawops.orchestrator.task.ContextSnapshotCodec;
import com.clawops.orchestrator.task.JpaTaskStore;
import com.clawops.orchestrator.task.OrchestratorTestApp;
import com.clawops.orchestrator.task.TaskEntityRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
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

/** JdbcApprovalStore 审批落库集成测试。 */
@Tag("it")
@Testcontainers
@EnabledIfDockerAvailable
@SpringBootTest(classes = OrchestratorTestApp.class)
@DisplayName("JdbcApprovalStore 审批落库（集成）")
class JdbcApprovalStoreIT {

  @Container @ServiceConnection
  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("pgvector/pgvector:0.8.7-pg17-trixie")
          .withDatabaseName("clawops")
          .withUsername("clawops")
          .withPassword("clawops");

  @Autowired private JdbcTemplate jdbc;
  @Autowired private com.fasterxml.jackson.databind.ObjectMapper objectMapper;
  @Autowired private TaskEntityRepository repository;

  private JdbcApprovalStore store;
  private String taskId;

  @BeforeEach
  void setUp() {
    store = new JdbcApprovalStore(jdbc, new ContextSnapshotCodec(objectMapper));
    taskId = UUID.randomUUID().toString();
    new JpaTaskStore(repository, objectMapper)
        .create(TaskRecord.create(taskId, "p", Instant.now()));
  }

  private ApprovalRecord createPending() {
    String approvalId = UUID.randomUUID().toString();
    ToolCall call = new ToolCall("call-1", "restart", Map.of("service", "order"));
    return store.createPending(approvalId, taskId, call, Permission.DANGEROUS);
  }

  @Test
  @DisplayName("create 后 find 返回 PENDING 与完整字段")
  void shouldCreateAndFind() {
    ApprovalRecord created = createPending();
    ApprovalRecord found = store.find(created.id()).orElseThrow();
    assertThat(found.status()).isEqualTo(ApprovalStatus.PENDING);
    assertThat(found.toolName()).isEqualTo("restart");
    assertThat(found.arguments()).containsEntry("service", "order");
    assertThat(found.requiredPermission()).isEqualTo(Permission.DANGEROUS);
    assertThat(found.decidedAt()).isNull();
  }

  @Test
  @DisplayName("findPendingByTask 返回 PENDING 审批")
  void shouldFindPendingByTask() {
    createPending();
    createPending();
    assertThat(store.findPendingByTask(taskId)).hasSize(2);
  }

  @Test
  @DisplayName("decide approve 后状态为 APPROVED 且记录决策人与时间")
  void shouldDecideApprove() {
    ApprovalRecord created = createPending();
    ApprovalRecord decided = store.decide(created.id(), ApprovalStatus.APPROVED, "同意", "alice");
    assertThat(decided.status()).isEqualTo(ApprovalStatus.APPROVED);
    assertThat(decided.reason()).isEqualTo("同意");
    assertThat(decided.decidedBy()).isEqualTo("alice");
    assertThat(decided.decidedAt()).isNotNull();
    assertThat(store.findPendingByTask(taskId)).isEmpty();
  }

  @Test
  @DisplayName("decide reject 后状态为 REJECTED")
  void shouldDecideReject() {
    ApprovalRecord created = createPending();
    ApprovalRecord decided = store.decide(created.id(), ApprovalStatus.REJECTED, "拒绝", "bob");
    assertThat(decided.status()).isEqualTo(ApprovalStatus.REJECTED);
  }

  @Test
  @DisplayName("重复决策抛 ApprovalStateException 且落库不变")
  void shouldRejectDuplicateDecision() {
    ApprovalRecord created = createPending();
    store.decide(created.id(), ApprovalStatus.APPROVED, "同意", "alice");
    assertThatThrownBy(() -> store.decide(created.id(), ApprovalStatus.REJECTED, "反悔", "alice"))
        .isInstanceOf(ApprovalStateException.class);
    assertThat(store.find(created.id()).orElseThrow().status()).isEqualTo(ApprovalStatus.APPROVED);
  }
}
