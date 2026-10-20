package com.clawops.orchestrator.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.clawops.orchestrator.task.ContextSnapshotCodec;
import com.clawops.orchestrator.task.OrchestratorTestApp;
import java.util.Map;
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

/** JdbcAuditWriter 审计落库集成测试。 */
@Tag("it")
@Testcontainers
@EnabledIfDockerAvailable
@SpringBootTest(classes = OrchestratorTestApp.class)
@DisplayName("JdbcAuditWriter 审计落库（集成）")
class JdbcAuditWriterIT {

  @Container @ServiceConnection
  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("pgvector/pgvector:0.8.7-pg17-trixie")
          .withDatabaseName("clawops")
          .withUsername("clawops")
          .withPassword("clawops");

  @Autowired private JdbcTemplate jdbc;
  @Autowired private com.fasterxml.jackson.databind.ObjectMapper objectMapper;
  private JdbcAuditWriter writer;

  @BeforeEach
  void setUp() {
    writer = new JdbcAuditWriter(jdbc, new ContextSnapshotCodec(objectMapper));
  }

  @Test
  @DisplayName("write 落库审计行，detail jsonb 携带结构化字段")
  void shouldWriteAuditEntryWithDetailJsonb() {
    writer.write(null, "system", "task.create", Map.of("taskId", "t-1", "prompt", "诊断 CPU 飙高"));

    Map<String, Object> row =
        jdbc.queryForMap(
            "SELECT actor, action, task_id, detail->>'taskId' AS tid, detail->>'prompt' AS prompt"
                + " FROM audit_log WHERE action = 'task.create' ORDER BY created_at DESC LIMIT 1");
    assertThat(row.get("actor")).isEqualTo("system");
    assertThat(row.get("action")).isEqualTo("task.create");
    assertThat(row.get("task_id")).isNull();
    assertThat(row.get("tid")).isEqualTo("t-1");
    assertThat(row.get("prompt")).isEqualTo("诊断 CPU 飙高");
  }

  @Test
  @DisplayName("支持任务级审计：task_id 关联已存在任务")
  void shouldWriteTaskScopedAudit() {
    String taskId = java.util.UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO task (id, prompt, status) VALUES (?, ?, 'CREATED')",
        java.util.UUID.fromString(taskId),
        "p");
    writer.write(
        taskId, "agent", "tool.call", Map.of("tool", "metrics.query", "status", "success"));

    Integer cnt =
        jdbc.queryForObject(
            "SELECT count(*) FROM audit_log WHERE task_id = ?::uuid AND action = 'tool.call'",
            Integer.class,
            java.util.UUID.fromString(taskId));
    assertThat(cnt).isEqualTo(1);
  }
}
