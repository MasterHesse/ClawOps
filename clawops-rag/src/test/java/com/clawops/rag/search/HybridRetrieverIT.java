package com.clawops.rag.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.clawops.rag.chunk.Chunk;
import com.clawops.rag.embedding.FakeEmbeddingProvider;
import com.clawops.rag.store.PgVectorStore;
import com.clawops.rag.store.RetrievedChunk;
import java.sql.Connection;
import java.sql.DriverManager;
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

/** HybridRetriever 混合检索集成测试（向量 + 全文 + RRF）。 */
@Tag("it")
@Testcontainers
@EnabledIfDockerAvailable
@DisplayName("HybridRetriever 混合检索（集成）")
class HybridRetrieverIT {

  @Container
  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("pgvector/pgvector:0.8.7-pg17-trixie")
          .withDatabaseName("clawops")
          .withUsername("clawops")
          .withPassword("clawops");

  private HybridRetriever retriever;

  @BeforeEach
  void setUp() {
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .locations("classpath:db/migration")
        .load()
        .migrate();
    DataSource ds =
        new SimpleDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    PgVectorStore store = new PgVectorStore(ds);
    FakeEmbeddingProvider embedding = new FakeEmbeddingProvider();
    CjkBigramTokenizer tokenizer = new CjkBigramTokenizer();
    retriever = new HybridRetriever(store, embedding, tokenizer);

    String docId = store.insertDoc("故障手册", "md", 500, 80, 200);
    List<Chunk> chunks =
        List.of(
            new Chunk(1, "连接池耗尽导致订单服务 502，需调大 maxActive"),
            new Chunk(2, "GC 日志显示 Full GC 频发，疑似内存泄漏"),
            new Chunk(3, "Redis 缓存命中率从 95% 降至 40%"));
    store.insertChunks(
        docId, chunks, embedding.embedAll(chunks.stream().map(Chunk::content).toList()));
  }

  @Test
  @DisplayName("查询命中相关 chunk，结果含 docTitle 与 seq")
  void shouldRetrieveRelevantChunks() {
    List<RetrievedChunk> hits = retriever.retrieve("连接池耗尽 502", 3);
    assertThat(hits).isNotEmpty();
    assertThat(hits.get(0).docTitle()).isEqualTo("故障手册");
    assertThat(hits).anyMatch(h -> h.content().contains("连接池"));
  }

  @Test
  @DisplayName("topK 可配，结果不超过 topK")
  void shouldRespectTopK() {
    assertThat(retriever.retrieve("诊断", 2)).hasSizeLessThanOrEqualTo(2);
  }

  /** 极简 DataSource 实现（DriverManager 包装）。 */
  private record SimpleDataSource(String url, String user, String password) implements DataSource {

    @Override
    public Connection getConnection() {
      try {
        return DriverManager.getConnection(url, user, password);
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
  }
}
