package com.clawops.rag.store;

import com.clawops.rag.chunk.Chunk;
import com.clawops.rag.search.CjkBigramTokenizer;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;

/**
 * pgvector 知识库存储：文档与切分入库（含 HNSW 余弦向量 + tsvector 全文），向量与全文检索。
 *
 * <p>纯 JDBC（{@link DataSource} 由 gateway 注入），全量参数化；embedding 以字符串 '[v1,v2,...]'::vector 写入/查询； tsv
 * 由应用层 CJK bigram 后 to_tsvector('simple', ?)。
 */
public class PgVectorStore {

  private final DataSource dataSource;
  private final CjkBigramTokenizer tokenizer = new CjkBigramTokenizer();

  public PgVectorStore(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  /** 新建文档，返回 docId。 */
  public String insertDoc(
      String title, String sourceType, int chunkSize, int overlap, int contentLength) {
    String docId = UUID.randomUUID().toString();
    try (var conn = dataSource.getConnection();
        PreparedStatement ps =
            conn.prepareStatement(
                "INSERT INTO kb_doc (id, title, source_type, chunk_size, overlap,"
                    + " content_length, status) VALUES (?, ?, ?, ?, ?, ?, 'READY')")) {
      ps.setObject(1, UUID.fromString(docId), Types.OTHER);
      ps.setString(2, title);
      ps.setString(3, sourceType);
      ps.setInt(4, chunkSize);
      ps.setInt(5, overlap);
      ps.setInt(6, contentLength);
      ps.executeUpdate();
      return docId;
    } catch (SQLException e) {
      throw new IllegalStateException("插入 kb_doc 失败", e);
    }
  }

  /** 批量插入切分（含 embedding 与 tsv）。 */
  public void insertChunks(String docId, List<Chunk> chunks, List<float[]> embeddings) {
    if (chunks.size() != embeddings.size()) {
      throw new IllegalArgumentException("chunks 与 embeddings 数量不一致");
    }
    try (var conn = dataSource.getConnection();
        PreparedStatement ps =
            conn.prepareStatement(
                "INSERT INTO kb_chunk (id, doc_id, seq, content, embedding, tsv, created_at)"
                    + " VALUES (?, ?, ?, ?, ?::vector, to_tsvector('simple', ?), ?)")) {
      for (int i = 0; i < chunks.size(); i++) {
        Chunk c = chunks.get(i);
        ps.setObject(1, UUID.randomUUID(), Types.OTHER);
        ps.setObject(2, UUID.fromString(docId), Types.OTHER);
        ps.setInt(3, c.seq());
        ps.setString(4, c.content());
        ps.setString(5, vectorLiteral(embeddings.get(i)));
        ps.setString(6, tokenizer.tokenize(c.content()));
        ps.setTimestamp(7, Timestamp.from(Instant.now()));
        ps.addBatch();
      }
      ps.executeBatch();
    } catch (SQLException e) {
      throw new IllegalStateException("插入 kb_chunk 失败", e);
    }
  }

  /** 向量余弦检索（距离升序）。 */
  public List<RetrievedChunk> searchByVector(float[] queryVector, int topK) {
    String literal = vectorLiteral(queryVector);
    try (var conn = dataSource.getConnection();
        PreparedStatement ps =
            conn.prepareStatement(
                "SELECT c.id, c.doc_id, c.seq, c.content,"
                    + " (c.embedding <=> ?::vector) AS distance, d.title"
                    + " FROM kb_chunk c JOIN kb_doc d ON c.doc_id = d.id"
                    + " WHERE d.status = 'READY'"
                    + " ORDER BY c.embedding <=> ?::vector ASC LIMIT ?")) {
      ps.setString(1, literal);
      ps.setString(2, literal);
      ps.setInt(3, topK);
      return mapRows(ps.executeQuery(), "vector");
    } catch (SQLException e) {
      throw new IllegalStateException("向量检索失败", e);
    }
  }

  /** 全文检索（tsv @@ plainto_tsquery，rank 降序）。 */
  public List<RetrievedChunk> searchByFulltext(String tokenizedQuery, int topK) {
    try (var conn = dataSource.getConnection();
        PreparedStatement ps =
            conn.prepareStatement(
                "SELECT c.id, c.doc_id, c.seq, c.content,"
                    + " ts_rank(c.tsv, query) AS rank, d.title"
                    + " FROM kb_chunk c JOIN kb_doc d ON c.doc_id = d.id,"
                    + " plainto_tsquery('simple', ?) AS query"
                    + " WHERE d.status = 'READY' AND c.tsv @@ query"
                    + " ORDER BY rank DESC LIMIT ?")) {
      ps.setString(1, tokenizedQuery);
      ps.setInt(2, topK);
      return mapRows(ps.executeQuery(), "fulltext");
    } catch (SQLException e) {
      throw new IllegalStateException("全文检索失败", e);
    }
  }

  /** 删除文档（级联切分）。 */
  public void deleteDoc(String docId) {
    try (var conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement("DELETE FROM kb_doc WHERE id = ?")) {
      ps.setObject(1, UUID.fromString(docId), Types.OTHER);
      ps.executeUpdate();
    } catch (SQLException e) {
      throw new IllegalStateException("删除 kb_doc 失败", e);
    }
  }

  private List<RetrievedChunk> mapRows(ResultSet rs, String source) throws SQLException {
    List<RetrievedChunk> result = new ArrayList<>();
    while (rs.next()) {
      result.add(
          new RetrievedChunk(
              rs.getString("id"),
              rs.getString("doc_id"),
              rs.getString("title"),
              rs.getInt("seq"),
              rs.getString("content"),
              rs.getDouble(source.equals("vector") ? "distance" : "rank"),
              source));
    }
    return result;
  }

  static String vectorLiteral(float[] vec) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < vec.length; i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append(vec[i]);
    }
    return sb.append(']').toString();
  }
}
