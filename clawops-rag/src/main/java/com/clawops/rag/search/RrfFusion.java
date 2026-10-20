package com.clawops.rag.search;

import com.clawops.rag.store.RetrievedChunk;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 倒数排名融合（Reciprocal Rank Fusion）：score = Σ 1/(k + rank)，跨向量与全文两榜融合，Top-K 可配。
 *
 * <p>双榜都出现的 chunk 标记 source=hybrid，单榜则保留原榜标记（vector/fulltext）。
 */
public class RrfFusion {

  public List<RetrievedChunk> fuse(
      List<RetrievedChunk> vectorRanked, List<RetrievedChunk> fulltextRanked, int topK, int k) {
    Map<String, Entry> map = new LinkedHashMap<>();
    accumulate(map, vectorRanked, k, "vector");
    accumulate(map, fulltextRanked, k, "fulltext");
    List<Entry> entries = new ArrayList<>(map.values());
    entries.sort((a, b) -> Double.compare(b.score, a.score));
    List<RetrievedChunk> result = new ArrayList<>();
    for (int i = 0; i < Math.min(topK, entries.size()); i++) {
      result.add(entries.get(i).toChunk());
    }
    return result;
  }

  private void accumulate(
      Map<String, Entry> map, List<RetrievedChunk> ranked, int k, String source) {
    for (int rank = 0; rank < ranked.size(); rank++) {
      RetrievedChunk c = ranked.get(rank);
      map.computeIfAbsent(c.chunkId(), id -> new Entry(c)).add(1.0 / (k + rank + 1), source);
    }
  }

  private static final class Entry {

    final RetrievedChunk chunk;
    double score;
    boolean fromVector;
    boolean fromFulltext;

    Entry(RetrievedChunk chunk) {
      this.chunk = chunk;
    }

    void add(double delta, String source) {
      score += delta;
      if ("vector".equals(source)) {
        fromVector = true;
      } else {
        fromFulltext = true;
      }
    }

    RetrievedChunk toChunk() {
      String source = fromVector && fromFulltext ? "hybrid" : (fromVector ? "vector" : "fulltext");
      return new RetrievedChunk(
          chunk.chunkId(),
          chunk.docId(),
          chunk.docTitle(),
          chunk.seq(),
          chunk.content(),
          score,
          source);
    }
  }
}
