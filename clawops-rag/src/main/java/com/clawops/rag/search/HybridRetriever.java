package com.clawops.rag.search;

import com.clawops.rag.embedding.EmbeddingProvider;
import com.clawops.rag.store.PgVectorStore;
import com.clawops.rag.store.RetrievedChunk;
import java.util.List;

/**
 * 混合检索：向量（HNSW 余弦）+ 全文（tsvector GIN）→ 应用层 RRF 融合，Top-K 可配。
 *
 * <p>注入 PgVectorStore + EmbeddingProvider + CjkBigramTokenizer；检索 query 经 tokenization 喂全文、经 embed
 * 喂向量。
 */
public class HybridRetriever {

  private static final int DEFAULT_TOP_K = 5;
  private static final int RRF_K = 60;

  private final PgVectorStore store;
  private final EmbeddingProvider embeddingProvider;
  private final CjkBigramTokenizer tokenizer;
  private final RrfFusion fusion = new RrfFusion();

  public HybridRetriever(
      PgVectorStore store, EmbeddingProvider embeddingProvider, CjkBigramTokenizer tokenizer) {
    this.store = store;
    this.embeddingProvider = embeddingProvider;
    this.tokenizer = tokenizer;
  }

  public List<RetrievedChunk> retrieve(String query, int topK) {
    float[] queryVector = embeddingProvider.embed(query);
    String tokenizedQuery = tokenizer.tokenize(query);
    List<RetrievedChunk> vectorHits = store.searchByVector(queryVector, topK);
    List<RetrievedChunk> fulltextHits = store.searchByFulltext(tokenizedQuery, topK);
    return fusion.fuse(vectorHits, fulltextHits, topK, RRF_K);
  }

  public List<RetrievedChunk> retrieve(String query) {
    return retrieve(query, DEFAULT_TOP_K);
  }
}
