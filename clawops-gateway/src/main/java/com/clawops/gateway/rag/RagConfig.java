package com.clawops.gateway.rag;

import com.clawops.rag.RagService;
import com.clawops.rag.chunk.Chunker;
import com.clawops.rag.embedding.EmbeddingProvider;
import com.clawops.rag.embedding.FakeEmbeddingProvider;
import com.clawops.rag.parse.DocumentParserFactory;
import com.clawops.rag.search.CjkBigramTokenizer;
import com.clawops.rag.store.PgVectorStore;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 装配 RAG 组件（PgVectorStore / RagService / EmbeddingProvider 等）。E2 引入 LangChain4j 条件实现。 */
@Configuration
public class RagConfig {

  @Bean
  CjkBigramTokenizer cjkBigramTokenizer() {
    return new CjkBigramTokenizer();
  }

  @Bean
  Chunker chunker() {
    return new Chunker();
  }

  @Bean
  DocumentParserFactory documentParserFactory() {
    return new DocumentParserFactory();
  }

  @Bean
  EmbeddingProvider embeddingProvider() {
    // 无 LLM key 时（CI / 本机未配置）使用确定性 Fake；E2 按 clawops.llm.api-key 条件注入真实实现
    return new FakeEmbeddingProvider();
  }

  @Bean
  PgVectorStore pgVectorStore(DataSource dataSource) {
    return new PgVectorStore(dataSource);
  }

  @Bean
  RagService ragService(
      PgVectorStore store,
      EmbeddingProvider embeddingProvider,
      DocumentParserFactory parserFactory,
      Chunker chunker) {
    return new RagService(store, embeddingProvider, parserFactory, chunker);
  }
}
