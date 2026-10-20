package com.clawops.rag;

import com.clawops.rag.chunk.Chunk;
import com.clawops.rag.chunk.Chunker;
import com.clawops.rag.embedding.EmbeddingProvider;
import com.clawops.rag.parse.DocumentParserFactory;
import com.clawops.rag.parse.ParsedDocument;
import com.clawops.rag.search.CjkBigramTokenizer;
import com.clawops.rag.search.HybridRetriever;
import com.clawops.rag.store.PgVectorStore;
import com.clawops.rag.store.RetrievedChunk;
import java.util.List;
import java.util.Locale;

/**
 * RAG 编排：灌入（解析 → 切分 → embedding → 入库）与检索（混合检索 → 引用溯源）。
 *
 * <p>引用溯源：检索结果 {@link RetrievedChunk} 携带 docTitle / seq / content / source，供前端展示与跳转。
 */
public class RagService {

  private final PgVectorStore store;
  private final EmbeddingProvider embedding;
  private final DocumentParserFactory parserFactory;
  private final Chunker chunker;
  private final HybridRetriever retriever;

  public RagService(
      PgVectorStore store,
      EmbeddingProvider embedding,
      DocumentParserFactory parserFactory,
      Chunker chunker) {
    this.store = store;
    this.embedding = embedding;
    this.parserFactory = parserFactory;
    this.chunker = chunker;
    this.retriever = new HybridRetriever(store, embedding, new CjkBigramTokenizer());
  }

  /** 灌入文档：解析 → 切分 → embedding → 入库，返回 docId 与 chunk 数。 */
  public IngestResult ingest(String fileName, byte[] data, int chunkSize, int overlap) {
    ParsedDocument doc = parserFactory.parse(fileName, data);
    List<Chunk> chunks = chunker.split(doc.text(), chunkSize, overlap);
    if (chunks.isEmpty()) {
      throw new RagIngestException("文档无有效可切分内容: " + fileName);
    }
    List<float[]> embeddings = embedding.embedAll(chunks.stream().map(Chunk::content).toList());
    String docId =
        store.insertDoc(
            doc.title(), extensionOf(fileName), chunkSize, overlap, doc.text().length());
    store.insertChunks(docId, chunks, embeddings);
    return new IngestResult(docId, chunks.size());
  }

  /** 检索：返回带引用的切分。 */
  public List<RetrievedChunk> retrieve(String query, int topK) {
    return retriever.retrieve(query, topK);
  }

  public void deleteDoc(String docId) {
    store.deleteDoc(docId);
  }

  private static String extensionOf(String fileName) {
    if (fileName == null) {
      return "txt";
    }
    int dot = fileName.lastIndexOf('.');
    return dot > 0 ? fileName.substring(dot + 1).toLowerCase(Locale.ROOT) : "txt";
  }

  /** 灌入结果。 */
  public record IngestResult(String docId, int chunkCount) {}
}
