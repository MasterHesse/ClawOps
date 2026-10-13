package com.clawops.rag.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 集成测试模板（M0）：PG17 + pgvector 容器。
 *
 * <p>约定（conventions/testing.md）：集成测试一律 Testcontainers，禁止连接 dev/prod 实例；类名 *IT 由 failsafe 执行，本地可
 * -DskipITs 跳过。
 */
@Tag("it")
@Testcontainers
@EnabledIfDockerAvailable
@DisplayName("PG17 + pgvector 容器（集成测试模板）")
class PgVectorContainerIT {

  @Container
  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("pgvector/pgvector:0.8.7-pg17-trixie")
          .withDatabaseName("clawops")
          .withUsername("clawops")
          .withPassword("clawops");

  @Test
  @DisplayName("应支持 PG 方言基线：uuid 主键默认值 gen_random_uuid()")
  void shouldSupportUuidDefault() throws SQLException {
    try (Connection conn = openConnection();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT gen_random_uuid()")) {
      assertThat(rs.next()).isTrue();
      assertThat(rs.getString(1)).isNotBlank();
    }
  }

  @Test
  @DisplayName("应支持 pgvector 扩展与向量类型")
  void shouldSupportVectorExtension() throws SQLException {
    try (Connection conn = openConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute("CREATE EXTENSION IF NOT EXISTS vector");
      try (ResultSet rs = stmt.executeQuery("SELECT '[1,2,3]'::vector AS embedding")) {
        assertThat(rs.next()).isTrue();
        assertThat(rs.getString("embedding")).isEqualTo("[1,2,3]");
      }
    }
  }

  private Connection openConnection() throws SQLException {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
  }
}
