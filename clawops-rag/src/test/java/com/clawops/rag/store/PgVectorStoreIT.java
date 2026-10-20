package com.clawops.rag.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.clawops.rag.chunk.Chunk;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** PgVectorStore 向量入库与检索集成测试（pgvector HNSW + tsvector）。 */
@Tag("it")
@Testcontainers
@EnabledIfDockerAvailable
@DisplayName("PgVectorStore 向量与全文检索（集成）")
class PgVectorStoreIT {

  @Container
  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("pgvector/pgvector:0.8.7-pg17-trixie")
          .withDatabaseName("clawops")
          .withUsername("clawops")
          .withPassword("clawops");

  private PgVectorStore store;
  private DataSource dataSource;

  @BeforeEach
  void setUp() {
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .locations("classpath:db/migration")
        .load()
        .migrate();
    dataSource =
        new DataSource() {
          @Override
          public Connection getConnection() {
            try {
              return DriverManager.getConnection(
                  POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
            } catch (Exception e) {
              throw new IllegalStateException(e);
            }
          }

          @Override
          public Connection getConnection(String u, String p) {
            return getConnection();
          }

          @Override
          public java.io.PrintWriter getLogWriter() {
            return null;
          }

          @Override
          public void setLogWriter(java.io.PrintWriter out) {}

          @Override
          public void setLoginTimeout(int seconds) {}

          @Override
          public int getLoginTimeout() {
            return 0;
          }

          @Override
          public java.util.logging.Logger getParentLogger() {
            return null;
          }

          @Override
          public <T> T unwrap(Class<T> iface) {
            throw new UnsupportedOperationException();
          }

          @Override
          public boolean isWrapperFor(Class<?> iface) {
            return false;
          }
        };
    store = new PgVectorStore(dataSource);
  }

  @Test
  @DisplayName("向量余弦检索：query 最近 chunk 排首位")
  void shouldSearchByVectorCosine() {
    String docId = store.insertDoc("诊断手册", "md", 500, 80, 100);
    List<Chunk> chunks =
        List.of(
            new Chunk(1, "连接池配置 maxActive=50"),
            new Chunk(2, "GC 日志 Full GC 频发"),
            new Chunk(3, "Redis 缓存命中率下降"));
    store.insertChunks(docId, chunks, List.of(oneHotVec(0), oneHotVec(1), oneHotVec(2)));

    List<RetrievedChunk> hits = store.searchByVector(oneHotVec(0), 3);
    assertThat(hits).hasSize(3);
    assertThat(hits.get(0).seq()).isEqualTo(1);
    assertThat(hits.get(0).content()).contains("连接池");
    assertThat(hits.get(0).source()).isEqualTo("vector");
  }

  @Test
  @DisplayName("全文检索：CJK bigram 命中中文关键词")
  void shouldSearchByFulltext() {
    String docId = store.insertDoc("故障手册", "md", 500, 80, 100);
    store.insertChunks(
        docId,
        List.of(new Chunk(1, "连接池耗尽导致订单服务 502"), new Chunk(2, "磁盘 IO 延迟过高")),
        List.of(oneHotVec(0), oneHotVec(1)));

    List<RetrievedChunk> hits =
        store.searchByFulltext(
            new com.clawops.rag.search.CjkBigramTokenizer().tokenize("连接池耗尽"), 5);
    assertThat(hits).isNotEmpty();
    assertThat(hits.get(0).content()).contains("连接池");
  }

  @Test
  @DisplayName("删除文档级联清除切分")
  void shouldDeleteDocCascade() {
    String docId = store.insertDoc("临时", "txt", 500, 80, 10);
    store.insertChunks(docId, List.of(new Chunk(1, "内容")), List.of(oneHotVec(0)));

    store.deleteDoc(docId);

    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps =
            conn.prepareStatement("SELECT count(*) FROM kb_chunk WHERE doc_id = ?::uuid")) {
      ps.setString(1, docId);
      try (ResultSet rs = ps.executeQuery()) {
        rs.next();
        assertThat(rs.getInt(1)).isZero();
      }
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /** 构造 1536 维 one-hot 向量（仅 pos 位为 1）。 */
  private static float[] oneHotVec(int pos) {
    float[] v = new float[1536];
    v[pos] = 1f;
    return v;
  }
}
