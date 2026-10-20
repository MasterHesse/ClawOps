package com.clawops.rag.it;

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
 * RAG 域 Flyway V2 迁移集成测试。
 *
 * <p>验证 V2__rag_domain.sql 创建 kb_doc / kb_chunk、vector 扩展、HNSW 与 GIN 索引。
 */
@Tag("it")
@Testcontainers
@EnabledIfDockerAvailable
@DisplayName("RAG 域 Flyway V2 迁移（集成）")
class FlywayRagDomainIT {

  @Container
  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("pgvector/pgvector:0.8.7-pg17-trixie")
          .withDatabaseName("clawops")
          .withUsername("clawops")
          .withPassword("clawops");

  @Test
  @DisplayName("应创建 kb_doc 与 kb_chunk 表并启用 vector 扩展")
  void shouldCreateRagDomainTables() throws SQLException {
    migrate();

    List<String> tables =
        queryStrings(
            "SELECT table_name FROM information_schema.tables"
                + " WHERE table_schema = 'public' AND table_name IN ('kb_doc','kb_chunk')"
                + " ORDER BY table_name");
    assertThat(tables).containsExactly("kb_chunk", "kb_doc");

    int vectorExt = queryInt("SELECT count(*) FROM pg_extension WHERE extname = 'vector'");
    assertThat(vectorExt).isEqualTo(1);
  }

  @Test
  @DisplayName("kb_chunk 应有 HNSW 余弦索引与 tsv GIN 索引，embedding 列不锁维度")
  void shouldCreateVectorAndFulltextIndexes() throws SQLException {
    migrate();

    int hnswIndexes =
        queryInt(
            "SELECT count(*) FROM pg_indexes WHERE schemaname = 'public'"
                + " AND tablename = 'kb_chunk' AND indexdef LIKE '%hnsw%vector_cosine_ops%'");
    assertThat(hnswIndexes).isEqualTo(1);

    int ginIndexes =
        queryInt(
            "SELECT count(*) FROM pg_indexes WHERE schemaname = 'public'"
                + " AND tablename = 'kb_chunk' AND indexdef LIKE '%gin%tsv%'");
    assertThat(ginIndexes).isEqualTo(1);

    List<String> embeddingType =
        queryStrings(
            "SELECT udt_name FROM information_schema.columns"
                + " WHERE table_schema = 'public' AND table_name = 'kb_chunk'"
                + " AND column_name = 'embedding'");
    assertThat(embeddingType).containsExactly("vector");

    int dimension =
        queryInt(
            "SELECT atttypmod FROM pg_attribute"
                + " WHERE attrelid = 'kb_chunk'::regclass AND attname = 'embedding'");
    assertThat(dimension).isEqualTo(1536);
  }

  @Test
  @DisplayName("应支持向量余弦距离与 tsvector 检索（PG 方言冒烟）")
  void shouldSupportVectorAndTsvectorQuery() throws SQLException {
    migrate();

    try (Connection conn = openConnection();
        PreparedStatement ps =
            conn.prepareStatement(
                "SELECT '[1,0]'::vector <=> '[1,1]'::vector AS dist,"
                    + " to_tsvector('simple', ?) @@ plainto_tsquery('simple', ?) AS hit")) {
      ps.setString(1, "connection pool exhausted");
      ps.setString(2, "pool exhausted");
      try (ResultSet rs = ps.executeQuery()) {
        assertThat(rs.next()).isTrue();
        assertThat(rs.getDouble("dist")).isBetween(0.0, 1.0);
        assertThat(rs.getBoolean("hit")).isTrue();
      }
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
