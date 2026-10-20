package com.clawops.orchestrator.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 任务域 Flyway V1 迁移集成测试（conventions/testing.md：一律 Testcontainers，禁连 dev/prod）。
 *
 * <p>验证 V1__task_domain.sql 在 PG17 + pgvector 镜像上可执行，且 5 张表符合 PG 方言基线（uuid 主键 / timestamptz /
 * jsonb）。
 */
@Tag("it")
@Testcontainers
@EnabledIfDockerAvailable
@DisplayName("任务域 Flyway V1 迁移（集成）")
class FlywayTaskDomainIT {

  @Container
  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("pgvector/pgvector:0.8.7-pg17-trixie")
          .withDatabaseName("clawops")
          .withUsername("clawops")
          .withPassword("clawops");

  @Test
  @DisplayName("应创建任务域 5 张表：task / task_step / audit_log / approval / eval_case")
  void shouldCreateTaskDomainTables() throws SQLException {
    migrate();

    List<String> tables =
        queryStrings(
            "SELECT table_name FROM information_schema.tables"
                + " WHERE table_schema = 'public' AND table_name IN"
                + " ('task','task_step','audit_log','approval','eval_case')"
                + " ORDER BY table_name");
    assertThat(tables).containsExactly("approval", "audit_log", "eval_case", "task", "task_step");
  }

  @Test
  @DisplayName("task 表应符合 PG 方言基线：uuid 主键 / timestamptz / jsonb")
  void shouldFollowPgDialectBaselineOnTaskTable() throws SQLException {
    migrate();

    List<String> columns =
        queryStrings(
            "SELECT column_name || ':' || data_type FROM information_schema.columns"
                + " WHERE table_schema = 'public' AND table_name = 'task'"
                + " AND column_name IN ('id','prompt','status','token_usage','context_messages',"
                + " 'created_at','updated_at') ORDER BY column_name");
    assertThat(columns)
        .contains(
            "context_messages:jsonb",
            "created_at:timestamp with time zone",
            "id:uuid",
            "prompt:text",
            "status:character varying",
            "token_usage:jsonb",
            "updated_at:timestamp with time zone");
  }

  @Test
  @DisplayName("task_step 应有 (task_id, seq) 唯一约束，approval 默认 PENDING")
  void shouldHaveStepUniqueConstraintAndApprovalDefault() throws SQLException {
    migrate();

    int stepUniqueIndexes =
        queryInt(
            "SELECT count(*) FROM pg_indexes WHERE schemaname = 'public'"
                + " AND tablename = 'task_step' AND indexdef LIKE '%UNIQUE%(task_id, seq)%'");
    assertThat(stepUniqueIndexes).isEqualTo(1);

    try (Connection conn = openConnection();
        PreparedStatement ps =
            conn.prepareStatement(
                "SELECT column_default FROM information_schema.columns"
                    + " WHERE table_schema = 'public' AND table_name = 'approval'"
                    + " AND column_name = 'status'");
        ResultSet rs = ps.executeQuery()) {
      assertThat(rs.next()).isTrue();
      assertThat(rs.getString(1)).containsIgnoringCase("PENDING");
    }
  }

  private void migrate() {
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .locations("classpath:db/migration")
        .load()
        .migrate();
  }

  private List<String> queryStrings(String sql) throws SQLException {
    try (Connection conn = openConnection();
        PreparedStatement ps = conn.prepareStatement(sql);
        ResultSet rs = ps.executeQuery()) {
      List<String> result = new ArrayList<>();
      while (rs.next()) {
        result.add(rs.getString(1));
      }
      return result;
    }
  }

  private int queryInt(String sql) throws SQLException {
    try (Connection conn = openConnection();
        PreparedStatement ps = conn.prepareStatement(sql);
        ResultSet rs = ps.executeQuery()) {
      rs.next();
      return rs.getInt(1);
    }
  }

  private Connection openConnection() throws SQLException {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
  }
}
