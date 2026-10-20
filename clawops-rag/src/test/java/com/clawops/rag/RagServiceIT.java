package com.clawops.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.clawops.rag.chunk.Chunker;
import com.clawops.rag.embedding.FakeEmbeddingProvider;
import com.clawops.rag.parse.DocumentParserFactory;
import com.clawops.rag.store.RetrievedChunk;
import java.nio.charset.StandardCharsets;
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

/** RagService 灌入与引用溯源集成测试。 */
@Tag("it")
@Testcontainers
@EnabledIfDockerAvailable
@DisplayName("RagService 灌入与检索引用（集成）")
class RagServiceIT {

  @Container
  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("pgvector/pgvector:0.8.7-pg17-trixie")
          .withDatabaseName("clawops")
          .withUsername("clawops")
          .withPassword("clawops");

  private RagService service;

  @BeforeEach
  void setUp() {
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .locations("classpath:db/migration")
        .load()
        .migrate();
    DataSource ds =
        new SimpleDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    service =
        new RagService(
            new com.clawops.rag.store.PgVectorStore(ds),
            new FakeEmbeddingProvider(),
            new DocumentParserFactory(),
            new Chunker());
  }

  @Test
  @DisplayName("灌入 md 文档：chunk 数 > 0，切分参数落库")
  void shouldIngestMarkdown() {
    String md = "# 连接池诊断\n\n连接池耗尽导致订单服务 502。maxActive=50 过小。\n\nGC 日志显示 Full GC 频发，疑似内存泄漏。";
    RagService.IngestResult result =
        service.ingest("diag.md", md.getBytes(StandardCharsets.UTF_8), 100, 20);

    assertThat(result.chunkCount()).isGreaterThan(0);
  }

  @Test
  @DisplayName("检索结果携带 docTitle / seq / content 引用")
  void shouldRetrieveWithCitations() {
    String md = "# 故障手册\n\n连接池耗尽导致 502。\n\nGC Full GC 频发内存泄漏。\n\nRedis 命中率下降。";
    service.ingest("manual.md", md.getBytes(StandardCharsets.UTF_8), 100, 20);

    List<RetrievedChunk> hits = service.retrieve("连接池 502", 3);
    assertThat(hits).isNotEmpty();
    assertThat(hits.get(0).docTitle()).isEqualTo("故障手册");
    assertThat(hits.get(0).seq()).isGreaterThanOrEqualTo(1);
    assertThat(hits.get(0).content()).isNotBlank();
  }

  @Test
  @DisplayName("空内容文档灌入抛 RagIngestException")
  void shouldRejectEmptyIngest() {
    assertThatThrownBy(
            () -> service.ingest("empty.txt", "   ".getBytes(StandardCharsets.UTF_8), 100, 20))
        .isInstanceOf(RagIngestException.class);
  }

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
